import Foundation

struct ApiError: LocalizedError {
    let message: String
    init(_ message: String) { self.message = message }
    var errorDescription: String? { message }
}

/// Il login ha più studenti collegati (genitore con più figli): bisogna sceglierne uno.
struct ChoiceNeeded: Error { let choices: [Student] }

struct Student: Hashable { let ident: String; let name: String; let school: String }

/// [key] distingue gli studenti che condividono le stesse credenziali.
struct Account: Hashable, Identifiable {
    let key: String; let uid: String; let name: String
    var id: String { key }
}

extension Error {
    var userText: String { (self as? ApiError)?.message ?? "Nessuna connessione. Controlla internet e riprova." }
}

final class Api {
    /// Dentro `Api.$cacheOnly.withValue(true)` le chiamate leggono solo i dati salvati, senza usare la rete.
    @TaskLocal static var cacheOnly = false

    private let fixed: String?
    private let defaults = UserDefaults.standard

    init(fixed: String? = nil) { self.fixed = fixed }

    private static let cacheRoot: URL = {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("api", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var url = dir
        try? url.setResourceValues(values)
        return dir
    }()

    static var downloads: URL {
        let dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("files", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    // MARK: account

    private func key(_ o: JSON) -> String { o.str("key").ifBlank ?? o.str("uid") }

    private func stored() -> [JSON] {
        guard let s = Keychain.get("accounts"), let d = s.data(using: .utf8),
              let a = (try? JSONSerialization.jsonObject(with: d)) as? [JSON] else { return [] }
        return a
    }

    private func save(_ list: [JSON]) {
        let d = try? JSONSerialization.data(withJSONObject: list)
        Keychain.put("accounts", d.flatMap { String(data: $0, encoding: .utf8) } ?? "[]")
    }

    private func account() -> JSON? { stored().first { key($0) == current } }

    var accounts: [Account] { stored().map { Account(key: key($0), uid: $0.str("uid"), name: $0.str("name")) } }
    var current: String? { fixed ?? defaults.string(forKey: "current") }
    var name: String { account()?.str("name") ?? "" }
    var uid: String { account()?.str("uid") ?? "" }

    private func token() -> String? { current.flatMap { Keychain.get("token|\($0)") } }
    private func id() -> String { current.flatMap { Keychain.get("id|\($0)") } ?? "" }

    /// Accede e salva l'account. Se ci sono più studenti e [student] è nullo lancia `ChoiceNeeded`.
    func login(uid: String, pass: String, student: Student? = nil) async throws {
        let (res, _) = try await auth(uid, pass, student?.ident, pickFirst: false)
        let key = student.map { "\(uid)|\($0.ident)" } ?? uid
        let name = student?.name.ifBlank ?? "\(res.str("firstName")) \(res.str("lastName"))".trimmed
        var account: JSON = ["key": key, "uid": uid, "pass": pass, "name": name]
        if let student { account["ident"] = student.ident }
        save(stored().filter { self.key($0) != key } + [account])
        defaults.set(key, forKey: "current")
        saveSession(key, res)
    }

    private func auth(_ uid: String, _ pass: String, _ ident: String?, pickFirst: Bool) async throws -> (JSON, String?) {
        var body: JSON = ["ident": ident ?? NSNull(), "pass": pass, "uid": uid]
        var r = try await request("POST", "/auth/login", body, nil)
        var used = ident
        if r.1.str("requestedAction") == "choiceIdent" {
            let choices = r.1.list("choices").map { Student(ident: $0.str("ident"), name: $0.str("name").pretty, school: $0.str("school").pretty) }
            if choices.isEmpty { throw ApiError("Nessuno studente collegato all'account") }
            if choices.count > 1 && !pickFirst { throw ChoiceNeeded(choices: choices) }
            used = choices[0].ident
            body["ident"] = used
            r = try await request("POST", "/auth/login", body, nil)
        }
        if r.1["token"] == nil { throw ApiError(r.0 == 422 ? "Credenziali errate" : "Errore \(r.0)") }
        return (r.1, used)
    }

    private func saveSession(_ key: String, _ res: JSON) {
        Keychain.put("token|\(key)", res.str("token"))
        Keychain.put("id|\(key)", res.str("ident").filter(\.isNumber))
    }

    func switchTo(_ key: String) { defaults.set(key, forKey: "current") }

    func logout() {
        guard let key = current else { return }
        let rest = stored().filter { self.key($0) != key }
        save(rest)
        Keychain.put("token|\(key)", nil)
        Keychain.put("id|\(key)", nil)
        try? FileManager.default.removeItem(at: Api.cacheRoot.appendingPathComponent(safe(key)))
        defaults.set(rest.first.map { self.key($0) }, forKey: "current")
    }

    private func relogin() async throws {
        guard let acc = account() else { throw ApiError("Nessun account") }
        let (res, _) = try await auth(acc.str("uid"), acc.str("pass"), acc.str("ident").ifBlank, pickFirst: true)
        saveSession(key(acc), res)
    }

    private func requireOnline() throws {
        if Api.cacheOnly { throw ApiError("Dato non disponibile offline") }
    }

    private func authed(_ call: () async throws -> (Int, Data)) async throws -> Data {
        try requireOnline()
        if token() == nil { try await relogin() }
        var r = try await call()
        if r.0 == 401 {
            try await relogin()
            r = try await call()
        }
        if r.0 != 200 { throw ApiError("Errore \(r.0)") }
        return r.1
    }

    private func student(_ path: String) -> String { "/students/\(id())\(path)" }

    func studentId() async throws -> String {
        try requireOnline()
        if token() == nil { try await relogin() }
        return id()
    }

    private func safe(_ s: String) -> String { String(s.map { $0.isASCII && ($0.isLetter || $0.isNumber) ? $0 : "_" }) }

    /// Le date nel percorso cambiano ogni giorno: vengono ignorate così la copia salvata resta valida.
    private func cacheFile(_ method: String, _ path: String) -> URL? {
        guard let key = current else { return nil }
        let name = String(safe(method + Rx("\\d{8}").replace(path, "D")).prefix(120))
        return Api.cacheRoot.appendingPathComponent(safe(key), isDirectory: true).appendingPathComponent("\(name).json")
    }

    private func json(_ method: String, _ path: String) async throws -> JSON {
        let file = cacheFile(method, path)
        if Api.cacheOnly {
            guard let file, let d = try? Data(contentsOf: file), let o = (try? JSONSerialization.jsonObject(with: d)) as? JSON else {
                throw ApiError("Nessun dato salvato")
            }
            return o
        }
        let data = try await authed { try await self.raw(method, self.student(path), nil, self.token()) }
        let o = ((try? JSONSerialization.jsonObject(with: data)) as? JSON) ?? [:]
        if let file {
            try? FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
            try? data.write(to: file)
        }
        return o
    }

    func get(_ path: String) async throws -> JSON { try await json("GET", path) }
    func post(_ path: String) async throws -> JSON { try await json("POST", path) }
    func bytes(_ path: String) async throws -> Data { try await authed { try await self.raw("GET", self.student(path), nil, self.token()) } }
    func postBytes(_ path: String) async throws -> Data { try await authed { try await self.raw("POST", self.student(path), nil, self.token()) } }

    // MARK: sito web

    /// Accede al sito web: i cookie restano in `HTTPCookieStorage.shared`, usato anche dalle pagine web dell'app.
    @discardableResult
    func webSession() async throws -> Bool {
        try requireOnline()
        guard let acc = account() else { return false }
        var req = URLRequest(url: URL(string: Endpoints.webLogin)!)
        req.httpMethod = "POST"
        req.setValue("application/x-www-form-urlencoded; charset=UTF-8", forHTTPHeaderField: "Content-Type")
        let enc = { (s: String) in s.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? s }
        req.httpBody = Data("cid=&uid=\(enc(acc.str("uid")))&pwd=\(enc(acc.str("pass")))&pin=&target=".utf8)
        let (_, response) = try await URLSession.shared.data(for: req)
        let cookies = HTTPCookieStorage.shared.cookies(for: URL(string: Endpoints.web)!) ?? []
        return (response as? HTTPURLResponse)?.statusCode == 200 && !cookies.isEmpty
    }

    func webPage(_ url: String) async throws -> String {
        try requireOnline()
        try await webSession()
        let (data, response) = try await URLSession.shared.data(from: URL(string: url)!)
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        if code >= 400 { throw ApiError("Errore \(code) durante la connessione web") }
        return String(decoding: data, as: UTF8.self)
    }

    // MARK: rete

    private func request(_ method: String, _ path: String, _ body: JSON?, _ token: String?) async throws -> (Int, JSON) {
        let data = try body.map { try JSONSerialization.data(withJSONObject: $0) }
        let (code, bytes) = try await raw(method, path, data, token)
        return (code, ((try? JSONSerialization.jsonObject(with: bytes)) as? JSON) ?? [:])
    }

    private func raw(_ method: String, _ path: String, _ body: Data?, _ token: String?) async throws -> (Int, Data) {
        var req = URLRequest(url: URL(string: Endpoints.rest + path)!)
        req.httpMethod = method
        req.setValue(Endpoints.userAgent, forHTTPHeaderField: "User-Agent")
        req.setValue(Endpoints.apiKey, forHTTPHeaderField: "Z-Dev-Apikey")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token { req.setValue(token, forHTTPHeaderField: "Z-Auth-Token") }
        req.httpBody = body
        let (data, response) = try await URLSession.shared.data(for: req)
        return ((response as? HTTPURLResponse)?.statusCode ?? 0, data)
    }
}

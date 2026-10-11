import Foundation

/// Riconosce verifiche e interrogazioni nel testo dell'agenda, senza IA.
/// Una parola come "verifica" o "compito" conta solo se annuncia la prova, non se la nomina
/// soltanto ("esercizi in preparazione al compito", "ripassare per la verifica", "correzione della verifica").
enum TestDetector {
    private static let keyword = Rx("\\b(verifica|verifiche|interrogazione|interrogazioni|interrogati|interrogata|interrogato|compito|test|prova|esame|simulazione)\\b")

    /// Parole che, prima della verifica nella stessa frase, indicano che se ne parla soltanto.
    private static let beforeCue = Rx("prepar|in vista|ripass|studia|studio|studiare|eserci|correg|correz|consegn|svolg|risultat|ritir|riguard|argoment|esito|voti d|valutazion")

    /// La verifica preceduta da una preposizione articolata ("per la", "della", "alla"...) è nominata, non annunciata.
    private static let beforePreposition = Rx(
        "((\\bper|\\bdella|\\bdel|\\bdello|\\balla|\\bal|\\ballo|\\bsulla|\\bsul|\\bnella|\\bnel|\\bdalla|\\bdal|\\bdopo|\\bprima|\\bcon)\\s+(la\\s+|il\\s+|lo\\s+|l')?|\\b(dell|all|nell|sull|dall)')\\s*$"
    )

    /// "compito per casa" o "compiti a casa" sono compiti, non verifiche.
    private static let homeworkAfter = Rx("^\\s*(per|a)\\s+casa")

    /// "prova" è anche un verbo: conta solo come "prova scritta/orale/pratica/di ..." e simili.
    private static let provaAfter = Rx("^\\s*(scritta|orale|pratica|di|d'|comune|intermedia|finale|strutturata|invalsi|parallela)")

    private static let stops = Set(".;!?\n".utf16)

    static func isTest(_ text: String) -> Bool {
        let s = text.plain
        let t = s as NSString
        for m in keyword.matches(s) {
            let start = m.range.location, end = start + m.range.length
            var sentenceStart = 0
            var i = start - 1
            while i >= 0 {
                if stops.contains(t.character(at: i)) { sentenceStart = i + 1; break }
                i -= 1
            }
            let from = max(sentenceStart, start - 45)
            let before = t.substring(with: NSRange(location: from, length: start - from))
            let after = String(t.substring(from: end).prefix(20))
            let word = t.substring(with: m.range)
            if beforeCue.has(before) || beforePreposition.has(before) { continue }
            if word == "compito" && homeworkAfter.has(after) { continue }
            if word == "prova" && !provaAfter.has(after) { continue }
            return true
        }
        return false
    }
}

extension Event {
    /// Un evento dell'agenda che sembra una verifica o un'interrogazione.
    var isTest: Bool { TestDetector.isTest("\(notes) \(homework ? "" : subject)") }

    /// "Verifica" o "Interrogazione", per gli eventi riconosciuti come prova.
    var testKind: String { Rx("\\binterrog").has(notes.plain) ? "Interrogazione" : "Verifica" }

    /// Tipo dell'evento in parole: compito, verifica, interrogazione o evento.
    var kind: String { isTest ? testKind : homework ? "Compito" : "Evento" }
}

/// Avvisa quando il proprio nome compare in agenda (es. "Interrogazione: Rossi, Bianchi").
enum Mentions {
    /// Di base il cognome: tutto tranne il primo nome ("Matteo De Rossi" → "De Rossi").
    static func defaultNames(_ accountName: String) -> [String] {
        let words = accountName.trimmed.split(whereSeparator: \.isWhitespace).map(String.init)
        let name = words.count > 1 ? words.dropFirst().joined(separator: " ") : accountName.trimmed
        return name.isEmpty ? [] : [name]
    }

    static func names(_ account: Account) -> [String] {
        guard let raw = UserDefaults.standard.string(forKey: "names|\(account.key)") else { return defaultNames(account.name) }
        return raw.split(separator: ",").map { String($0).trimmed }.filter { !$0.isEmpty }
    }

    static func save(_ account: Account, _ names: String) {
        UserDefaults.standard.set(names, forKey: "names|\(account.key)")
    }

    static func matches(_ text: String, _ names: [String]) -> Bool {
        let t = text.plain
        return names.map { $0.plain.trimmed }.filter { $0.count >= 3 }.contains { n in
            Rx("(?<![\\p{L}])" + NSRegularExpression.escapedPattern(for: n) + "(?![\\p{L}])").has(t)
        }
    }
}

/// Classeviva spesso lascia vuota la materia degli eventi dell'agenda e mette solo il docente.
/// La materia si ricava dalle lezioni dello stesso docente; se ne insegna più di una vince quella
/// citata nel testo dell'evento, altrimenti quella con più lezioni.
struct Teachers {
    private let subjects: [String: [String]]

    init(_ lessons: [Lesson]) {
        var counts: [String: [String: Int]] = [:]
        var order: [String: [String]] = [:]
        for l in lessons where !l.subject.trimmed.isEmpty {
            for author in l.author.split(separator: ",").map({ String($0).plain.trimmed }) where !author.isEmpty {
                if counts[author]?[l.subject] == nil { order[author, default: []].append(l.subject) }
                counts[author, default: [:]][l.subject, default: 0] += 1
            }
        }
        var subjects: [String: [String]] = [:]
        for (author, list) in order {
            let c = counts[author] ?? [:]
            subjects[author] = list.enumerated().sorted { a, b in
                let ca = c[a.element] ?? 0, cb = c[b.element] ?? 0
                return ca != cb ? ca > cb : a.offset < b.offset
            }.map(\.element)
        }
        self.subjects = subjects
    }

    func subject(_ e: Event) -> String {
        if !e.subject.trimmed.isEmpty { return e.subject }
        let options = subjects[e.author.plain.trimmed] ?? []
        if options.count <= 1 { return options.first ?? "" }
        let notes = e.notes.plain
        return options.first { s in s.plain.split(separator: " ").contains { $0.count >= 5 && notes.contains(String($0.prefix(6))) } } ?? options[0]
    }
}

extension String {
    /// Testo dell'agenda ripulito: spazi doppi tolti e frasi tutte in maiuscolo riportate in minuscolo.
    var tidy: String {
        var t = Rx("[ \\t]+([:;,.])").replace(trimmed, "$1")
        t = Rx("[ \\t]{2,}").replace(t, " ")
        let letters = t.filter(\.isLetter)
        if letters.count >= 8 && Double(letters.filter(\.isUppercase).count) > Double(letters.count) * 0.8 {
            return t.lowercased().capFirst
        }
        // "INTERROGAZIONE ELETTROTECNICA: Rossi, Bianchi" → "Interrogazione elettrotecnica: Rossi, Bianchi"
        if let m = Rx("^([\\p{Lu}'’ ]{6,})(?=[:\\-–])").first(t) {
            let ns = t as NSString
            return ns.replacingCharacters(in: m.range, with: ns.substring(with: m.range).trimmed.lowercased().capFirst)
        }
        return t
    }
}

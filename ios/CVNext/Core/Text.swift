import Foundation

typealias JSON = [String: Any]

extension Dictionary where Key == String, Value == Any {
    /// Come `optString` di Android: stringa vuota se manca o è null.
    func str(_ key: String) -> String {
        guard let v = self[key], !(v is NSNull) else { return "" }
        if let s = v as? String { return s }
        if let n = v as? NSNumber { return n.stringValue }
        return "\(v)"
    }
    func int(_ key: String, _ fallback: Int = 0) -> Int { (self[key] as? NSNumber)?.intValue ?? Int(str(key)) ?? fallback }
    func double(_ key: String) -> Double? {
        guard let v = self[key], !(v is NSNull) else { return nil }
        return (v as? NSNumber)?.doubleValue ?? Double(str(key))
    }
    func bool(_ key: String) -> Bool { (self[key] as? NSNumber)?.boolValue ?? (self[key] as? Bool) ?? false }
    func list(_ key: String) -> [JSON] { self[key] as? [JSON] ?? [] }
    func obj(_ key: String) -> JSON? { self[key] as? JSON }
}

/// Espressione regolare ICU, con lookbehind e classi Unicode come su Android.
struct Rx {
    let re: NSRegularExpression
    init(_ pattern: String) { re = try! NSRegularExpression(pattern: pattern) }

    private func all(_ s: String) -> NSRange { NSRange(location: 0, length: (s as NSString).length) }
    func has(_ s: String) -> Bool { re.firstMatch(in: s, range: all(s)) != nil }
    func matches(_ s: String) -> [NSTextCheckingResult] { re.matches(in: s, range: all(s)) }
    func first(_ s: String) -> NSTextCheckingResult? { re.firstMatch(in: s, range: all(s)) }
    func replace(_ s: String, _ template: String) -> String { re.stringByReplacingMatches(in: s, range: all(s), withTemplate: template) }
}

private let small: Set<String> = ["e", "ed", "di", "del", "della", "dei", "delle", "da", "a", "in", "per", "con", "la", "il"]

extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
    var capFirst: String { prefix(1).uppercased() + dropFirst() }
    var ifBlank: String? { trimmed.isEmpty ? nil : self }

    /// "MATEMATICA E COMPLEMENTI" → "Matematica e Complementi".
    var pretty: String {
        lowercased().split(separator: " ", omittingEmptySubsequences: false).enumerated()
            .map { i, w in i > 0 && small.contains(String(w)) ? String(w) : String(w).capFirst }
            .joined(separator: " ")
    }

    /// Minuscole e senza accenti, per confrontare testi scritti in modi diversi.
    var plain: String { folding(options: [.diacriticInsensitive], locale: nil).lowercased() }

    /// Primo nome: "Matteo De Rossi" → "Matteo".
    var firstWord: String { split(separator: " ").first.map(String.init) ?? self }
}

func num(_ x: Double) -> String { String(format: "%.2f", x).replacingOccurrences(of: ".", with: ",") }

/// Date come stringhe "aaaa-mm-gg", come le restituisce il registro.
enum Day {
    static let cal: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.locale = Locale(identifier: "it_IT")
        c.timeZone = .current
        return c
    }()

    private static func formatter(_ pattern: String, _ locale: String = "en_US_POSIX") -> DateFormatter {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: locale)
        f.timeZone = .current
        f.dateFormat = pattern
        return f
    }

    static let isoFmt = formatter("yyyy-MM-dd")
    static let basicFmt = formatter("yyyyMMdd")
    static let monthIn = formatter("yyyy-MM")
    static let longFmt = formatter("EEEE d MMMM", "it_IT")
    static let shortFmt = formatter("d MMM", "it_IT")
    static let monthFmt = formatter("MMMM yyyy", "it_IT")
    static let dayFmt = formatter("EEE", "it_IT")
    static let weekdayFmt = formatter("EEEE", "it_IT")

    static var today: Date { cal.startOfDay(for: Date()) }
    static func iso(_ d: Date) -> String { isoFmt.string(from: d) }
    static func basic(_ d: Date) -> String { basicFmt.string(from: d) }
    static func parse(_ s: String) -> Date? { isoFmt.date(from: String(s.prefix(10))) }
    static func add(_ d: Date, _ days: Int) -> Date { cal.date(byAdding: .day, value: days, to: d) ?? d }
    static func isSunday(_ d: Date) -> Bool { cal.component(.weekday, from: d) == 1 }
    static func number(_ d: Date) -> Int { cal.component(.day, from: d) }
    static func between(_ a: Date, _ b: Date) -> Int { cal.dateComponents([.day], from: cal.startOfDay(for: a), to: cal.startOfDay(for: b)).day ?? 0 }
    static func dayName(_ d: Date) -> String { dayFmt.string(from: d) }

    /// Il giorno di scuola dopo [from]: la domenica si salta.
    static func nextSchoolDay(_ from: Date = today) -> Date {
        let d = add(from, 1)
        return isSunday(d) ? add(d, 1) : d
    }
}

func longDate(_ s: String) -> String { Day.parse(s).map { Day.longFmt.string(from: $0).capFirst } ?? s }
func shortDate(_ s: String) -> String { Day.parse(s).map { Day.shortFmt.string(from: $0) } ?? s }
func monthName(_ s: String) -> String { Day.monthIn.date(from: String(s.prefix(7))).map { Day.monthFmt.string(from: $0).capFirst } ?? s }
func className(_ raw: String) -> String {
    let first = raw.split(separator: " ").first.map(String.init) ?? raw
    return (first.split(separator: "(").first.map(String.init) ?? first).trimmed
}

import Foundation
import Observation
import LocalAuthentication

extension Event {
    /// Chiave stabile di un compito: l'id di Classeviva, oppure data e testo se manca.
    var key: String { id != 0 ? "\(id)" : "\(date)_\(stableHash(notes))" }
}

/// hashValue cambia a ogni avvio: per salvare serve un hash fisso (djb2).
private func stableHash(_ s: String) -> UInt64 { s.utf8.reduce(5381) { ($0 &<< 5) &+ $0 &+ UInt64($1) } }

extension Grade {
    /// Chiave stabile di un voto, la stessa usata dalle notifiche.
    var key: String { id != 0 ? "\(id)" : "\(subject)_\(date)_\(value)" }
}

/// Compiti segnati come fatti. Restano solo su questo telefono, separati per account.
@MainActor @Observable
final class DoneStore {
    private let key: String
    private(set) var ids: Set<String>

    init(account: String?) {
        key = "done-\(account ?? "")"
        ids = Set(UserDefaults.standard.stringArray(forKey: key) ?? [])
    }

    func isDone(_ e: Event) -> Bool { ids.contains(e.key) }

    func toggle(_ e: Event) {
        if isDone(e) { ids.remove(e.key) } else { ids.insert(e.key) }
        UserDefaults.standard.set(Array(ids), forKey: key)
    }
}

/// Voti già visti: quelli nuovi hanno l'etichetta "Nuovo" finché non si apre la scheda Voti.
/// Al primo avvio tutti i voti presenti contano come già visti.
@MainActor @Observable
final class SeenGrades {
    private let key: String
    private var seen: Set<String>?

    init(account: String?) {
        key = "seenGrades-\(account ?? "")"
        seen = UserDefaults.standard.stringArray(forKey: key).map(Set.init)
    }

    func fresh(_ grades: [Grade]) -> Set<String> {
        guard let seen else { return [] }
        return Set(grades.map(\.key).filter { !seen.contains($0) })
    }

    func markSeen(_ grades: [Grade]) {
        let all = Set(grades.map(\.key))
        guard seen != all else { return }
        seen = all
        UserDefaults.standard.set(Array(all), forKey: key)
    }

    func start(_ grades: [Grade]) { if seen == nil { markSeen(grades) } }
}

/// Dove andare all'apertura: da una notifica ("notice:ID") o da una scorciatoia dell'icona.
@MainActor @Observable
final class Router {
    static let shared = Router()
    var target: String?
    var account: String?
}

/// Blocco dell'app con Face ID, Touch ID o codice del telefono.
enum AppLock {
    /// Tornando sull'app dopo questo tempo si richiede di nuovo lo sblocco.
    static let grace: TimeInterval = 60

    static var available: Bool { LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: nil) }

    static var biometryName: String {
        let c = LAContext()
        _ = c.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
        switch c.biometryType {
        case .faceID: return "Face ID"
        case .touchID: return "Touch ID"
        case .opticID: return "Optic ID"
        default: return "codice"
        }
    }

    static func unlock() async -> Bool {
        let c = LAContext()
        guard c.canEvaluatePolicy(.deviceOwnerAuthentication, error: nil) else { return true }
        return (try? await c.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: "Sblocca CV Next")) ?? false
    }
}

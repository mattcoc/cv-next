import Foundation
import Observation

@MainActor
protocol Loadable: AnyObject {
    var ready: Bool { get }
    var loading: Bool { get }
    var error: String? { get }
    func refresh() async
}

/// Un dato del registro: prima mostra la copia salvata, poi la aggiorna dalla rete.
@MainActor @Observable
final class Res<T>: Loadable {
    var data: T?
    var error: String?
    var loading = false
    var ready: Bool { data != nil }
    private let load: () async throws -> T

    init(_ load: @escaping () async throws -> T) { self.load = load }
    init(value: T) {
        load = { value }
        data = value
    }

    func refresh() async {
        loading = true
        error = nil
        if data == nil {
            data = try? await Api.$cacheOnly.withValue(true) { try await load() }
        }
        do {
            data = try await load()
        } catch is CancellationError {
        } catch {
            self.error = error.userText
        }
        loading = false
    }

    /// Carica solo se non è mai stato caricato (sezioni aperte di rado).
    func ensure() async { if data == nil && !loading { await refresh() } }
}

enum AppTab: Hashable { case today, day, grades, notices, functions }

/// Tutti i dati dell'account in uso.
@MainActor @Observable
final class Model {
    let api: Api
    var tab: AppTab = .today
    var showAccount = false
    var showInvite = false
    var update: IosRelease?
    @ObservationIgnored var debugName: String?

    let lessons: Res<[Lesson]>
    let grades: Res<[Grade]>
    let agenda: Res<[Event]>
    /// Agenda dell'intero anno scolastico: si carica solo aprendo l'elenco delle verifiche.
    let yearAgenda: Res<[Event]>
    let absences: Res<[Absence]>
    let notices: Res<[Notice]>
    let notes: Res<[NoteItem]>
    let didactics: Res<[DidTeacher]>
    let documents: Res<Documents>
    let periods: Res<[Period]>
    let school: Res<School>
    let payments: Res<[Payment]>
    let reminders = ReminderStore()

    init(api: Api) {
        self.api = api
        let today = Day.today
        lessons = Res { try await api.lessons(from: Day.add(today, -30), to: today) }
        grades = Res { try await api.grades() }
        agenda = Res { try await api.agenda() }
        yearAgenda = Res { try await api.yearAgenda() }
        absences = Res { try await api.absences() }
        notices = Res { try await api.notices() }
        notes = Res { try await api.notes() }
        didactics = Res { try await api.didactics() }
        documents = Res { try await api.documents() }
        periods = Res { try await api.periods() }
        school = Res { try await api.school() }
        payments = Res { try await api.payments() }
    }

    /// Dati di esempio, per gli screenshot automatici.
    init(debug: Void) {
        api = Api(fixed: "debug")
        debugName = "Marco Rossi"
        lessons = Res(value: DebugData.lessons)
        grades = Res(value: DebugData.grades)
        agenda = Res(value: DebugData.agenda)
        yearAgenda = Res(value: DebugData.yearAgenda)
        absences = Res(value: DebugData.absences)
        notices = Res(value: DebugData.notices)
        notes = Res(value: [])
        didactics = Res(value: [])
        documents = Res(value: Documents(docs: [], reports: []))
        periods = Res(value: DebugData.periods)
        school = Res(value: School(name: "Istituto Tecnico", city: "Padova PD"))
        payments = Res(value: [])
        reminders.debug()
        update = IosRelease(build: 999, date: nil, ipa: URL(string: "https://example.com/CVNext.ipa")!)
    }

    var name: String { debugName ?? api.name }
    var account: Account? { api.accounts.first { $0.key == api.current } }
    var myNames: [String] { account.map(Mentions.names) ?? (debugName.map(Mentions.defaultNames) ?? []) }
    var myReminders: [Reminder] { reminders.of(debugName != nil ? "debug" : api.current) }

    func refresh(_ list: [any Loadable]) async {
        await withTaskGroup(of: Void.self) { group in
            for r in list { group.addTask { await r.refresh() } }
        }
    }

    func loadAll() async {
        guard debugName == nil else { return }
        await refresh([lessons, grades, agenda, absences, notices, periods, school, payments])
    }
}

/// Promemoria scritti dall'utente su un giorno. Restano solo sul telefono, separati per account.
struct Reminder: Codable, Hashable, Identifiable { var id: String; var account: String; var date: String; var text: String }

@MainActor @Observable
final class ReminderStore {
    private(set) var all: [Reminder] = []

    init() {
        if let d = UserDefaults.standard.data(forKey: "reminders"), let list = try? JSONDecoder().decode([Reminder].self, from: d) { all = list }
    }

    private func save(_ list: [Reminder]) {
        all = list.sorted { $0.date < $1.date }
        UserDefaults.standard.set(try? JSONEncoder().encode(all), forKey: "reminders")
    }

    func of(_ account: String?) -> [Reminder] { all.filter { $0.account == account } }

    func add(_ account: String, _ date: String, _ text: String) {
        guard !text.trimmed.isEmpty else { return }
        save(all + [Reminder(id: UUID().uuidString, account: account, date: date, text: text.trimmed)])
    }

    func remove(_ r: Reminder) { save(all.filter { $0.id != r.id }) }

    /// I promemoria passati da più di un mese vengono tolti.
    func prune() { save(all.filter { $0.date >= Day.iso(Day.add(Day.today, -30)) }) }

    func debug() { all = DebugData.reminders }
}

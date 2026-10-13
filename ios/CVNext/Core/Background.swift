import Foundation
import BackgroundTasks
import UserNotifications

enum AlertKind: String, CaseIterable, Identifiable {
    case notices, grades, homework, didactics, notes, payments, mentions
    var id: String { rawValue }

    var title: String {
        switch self {
        case .notices: "Bacheca"
        case .grades: "Voti"
        case .homework: "Agenda"
        case .didactics: "Materiale dei docenti"
        case .notes: "Note"
        case .payments: "PagOnline"
        case .mentions: "Il tuo nome"
        }
    }

    var detail: String {
        switch self {
        case .notices: "Nuove comunicazioni e circolari"
        case .grades: "Quando viene inserito un voto"
        case .homework: "Nuovi compiti ed eventi in agenda"
        case .didactics: "Nuovi file condivisi dai docenti"
        case .notes: "Note disciplinari e annotazioni"
        case .payments: "Pagamenti da effettuare"
        case .mentions: "Quando sei nominato in agenda, ad esempio tra gli interrogati"
        }
    }

    var single: String {
        switch self {
        case .notices: "Nuova comunicazione"
        case .grades: "Nuovo voto"
        case .homework: "Nuovo in agenda"
        case .didactics: "Nuovo materiale"
        case .notes: "Nuova nota"
        case .payments: "Pagamento da fare"
        case .mentions: "Sei nominato in agenda"
        }
    }

    var icon: String {
        switch self {
        case .notices: "megaphone"
        case .grades: "star"
        case .homework: "calendar"
        case .didactics: "folder"
        case .notes: "exclamationmark.bubble"
        case .payments: "creditcard"
        case .mentions: "person.wave.2"
        }
    }

    /// Dove porta il tocco sulla notifica (le circolari aprono direttamente la comunicazione).
    var target: String {
        switch self {
        case .notices: "notices"
        case .grades: "grades"
        case .homework, .mentions: "homework"
        case .didactics, .notes, .payments: "today"
        }
    }

    /// Le notifiche scelte, salvate come "grades,notices".
    static var enabled: Set<AlertKind> {
        get { Set((UserDefaults.standard.string(forKey: "alerts") ?? "").split(separator: ",").compactMap { AlertKind(rawValue: String($0)) }) }
        set { UserDefaults.standard.set(newValue.map(\.rawValue).sorted().joined(separator: ","), forKey: "alerts") }
    }
}

/// Su iOS non si può controllare ogni ora: si chiede un aggiornamento in background e il sistema decide quando.
enum Background {
    static let id = "io.github.mattcoc.cvnext.check"

    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: id)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    static func requestPermission() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    @MainActor
    static func run() async {
        schedule()
        await Checker.run()
        await Evening.reschedule()
    }
}

@MainActor
enum Checker {
    static func run() async {
        let enabled = AlertKind.enabled
        let accounts = Api().accounts
        guard !enabled.isEmpty, !accounts.isEmpty else { return }
        let defaults = UserDefaults.standard
        for account in accounts {
            let api = Api(fixed: account.key)
            let who = accounts.count > 1 ? "\(account.name.firstWord.pretty) · " : ""
            // In background iOS concede pochi secondi: le richieste partono tutte insieme invece che una alla volta.
            let results = await withTaskGroup(of: (AlertKind, [(String, String)]?).self) { group in
                for alert in AlertKind.allCases where enabled.contains(alert) {
                    // Sul main actor: lo stato dell'Api resta su un solo thread, ma le attese di rete si sovrappongono.
                    group.addTask { @MainActor in (alert, try? await fetch(api, alert, account)) }
                }
                var all: [AlertKind: [(String, String)]] = [:]
                for await (alert, items) in group { if let items { all[alert] = items } }
                return all
            }
            for alert in AlertKind.allCases {
                guard let items = results[alert] else { continue }
                let key = "seen-\(alert.rawValue)-\(account.key)"
                if let seen = defaults.stringArray(forKey: key).map(Set.init) {
                    for item in items.filter({ !seen.contains($0.0) }).prefix(5) {
                        // Una circolare si apre direttamente toccando la notifica.
                        let target = alert == .notices ? "notice:\(item.0)" : alert.target
                        await post(alert, id: "\(alert.rawValue)-\(account.key)-\(item.0)", text: who + item.1,
                                   info: ["target": target, "account": account.key])
                    }
                }
                // Tiene solo gli id ancora presenti, così l'elenco non cresce all'infinito.
                defaults.set(items.map(\.0), forKey: key)
            }
        }
        defaults.set(Date().timeIntervalSince1970, forKey: "lastCheck")
    }

    private static func post(_ alert: AlertKind, id: String, text: String, info: [String: String] = [:]) async {
        let content = UNMutableNotificationContent()
        content.userInfo = info
        content.title = alert.single
        content.body = text
        content.sound = .default
        content.threadIdentifier = alert.rawValue
        try? await UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: id, content: content, trigger: nil))
    }

    private static func fetch(_ api: Api, _ alert: AlertKind, _ account: Account) async throws -> [(String, String)] {
        switch alert {
        case .notices:
            return try await api.notices().filter { !$0.read }.map { ("\($0.id)", $0.title) }
        case .grades:
            return try await api.grades().map { g in
                let id = g.id != 0 ? "\(g.id)" : "\(g.subject)_\(g.date)_\(g.value)"
                return (id, "\(g.subject): \(g.value)" + (g.type.trimmed.isEmpty ? "" : " (\(g.type.lowercased()))"))
            }
        case .homework:
            return try await api.agenda().map { ("\($0.id)", "\(shortDate($0.date)) · \($0.subject.ifBlank ?? $0.author): \($0.notes)") }
        case .didactics:
            return try await api.didactics().flatMap { t in t.folders.flatMap { f in f.contents.map { ("\($0.id)", "\(t.name): \($0.name)") } } }
        case .notes:
            return try await api.notes().map { ("\($0.id)", "\($0.type) · \($0.author)") }
        case .payments:
            return try await api.payments().map { ($0.id, "\($0.title) · € \($0.amount) entro il \(shortDate($0.due))") }
        case .mentions:
            let names = Mentions.names(account)
            let today = Day.iso(Date())
            return try await api.agenda().filter { $0.date >= today && Mentions.matches("\($0.notes) \($0.subject)", names) }.map {
                let what = $0.isTest ? "Verifica o interrogazione" : $0.homework ? "Compito" : "Evento"
                return ("m\($0.id)", "\(what) · \(longDate($0.date)) · \($0.subject.ifBlank ?? $0.author): \($0.notes)")
            }
        }
    }
}

/// Ogni sera, all'ora scelta, avvisa dei compiti, delle verifiche e dei promemoria del giorno di scuola successivo.
/// La notifica viene preparata in anticipo con i dati salvati e riprogrammata a ogni apertura o controllo.
@MainActor
enum Evening {
    static var on: Bool { UserDefaults.standard.bool(forKey: "evening") }
    static var hour: Int { UserDefaults.standard.object(forKey: "eveningHour") as? Int ?? 19 }

    static func reschedule() async {
        let center = UNUserNotificationCenter.current()
        let accounts = Api().accounts
        center.removePendingNotificationRequests(withIdentifiers: accounts.map { "evening-\($0.key)" })
        guard on else { return }
        let cal = Day.cal
        let now = Date()
        var fire = cal.date(bySettingHour: hour, minute: 0, second: 0, of: now) ?? now
        if fire <= now { fire = Day.add(fire, 1) }
        let day = Day.nextSchoolDay(cal.startOfDay(for: fire))
        let iso = Day.iso(day)
        let label = Day.between(fire, day) == 1 ? "Domani" : Day.weekdayFmt.string(from: day).capFirst
        let reminders = ReminderStore()
        for account in accounts {
            let api = Api(fixed: account.key)
            let events = (try? await Api.$cacheOnly.withValue(true) { try await api.agenda() }) ?? []
            let dayEvents = events.filter { $0.date == iso && ($0.homework || $0.isTest) }
            let mine = reminders.of(account.key).filter { $0.date == iso }
            if dayEvents.isEmpty && mine.isEmpty { continue }
            let tests = dayEvents.filter(\.isTest).count
            let homework = dayEvents.filter { $0.homework && !$0.isTest }.count
            var parts: [String] = []
            if tests > 0 { parts.append(tests == 1 ? "1 verifica" : "\(tests) verifiche") }
            if homework > 0 { parts.append(homework == 1 ? "1 compito" : "\(homework) compiti") }
            if !mine.isEmpty { parts.append(mine.count == 1 ? "1 promemoria" : "\(mine.count) promemoria") }
            let who = accounts.count > 1 ? " · \(account.name.firstWord.pretty)" : ""
            let lines = dayEvents.map { ($0.isTest ? "Verifica · " : "") + "\($0.subject.ifBlank ?? $0.author): \($0.notes)" } + mine.map { "• \($0.text)" }
            let content = UNMutableNotificationContent()
            content.title = "\(label): \(parts.joined(separator: ", "))\(who)"
            content.body = lines.joined(separator: "\n")
            content.sound = .default
            let comps = cal.dateComponents([.year, .month, .day, .hour, .minute], from: fire)
            let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
            try? await center.add(UNNotificationRequest(identifier: "evening-\(account.key)", content: content, trigger: trigger))
        }
        reminders.prune()
    }
}

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

    /// Dove porta il tocco sul riepilogo, quando le novità sono troppe per mostrarle tutte.
    var target: String {
        switch self {
        case .notices: "notices"
        case .grades: "grades"
        case .homework: "agenda"
        case .didactics: "didactics"
        case .notes: "notes"
        case .payments: "payments"
        case .mentions: "today"
        }
    }

    /// Titolo del riepilogo: "3 nuovi voti".
    func plural(_ n: Int) -> String {
        switch self {
        case .notices: "\(n) nuove circolari"
        case .grades: "\(n) nuovi voti"
        case .homework: "\(n) novità in agenda"
        case .didactics: "\(n) nuovi file dei docenti"
        case .notes: "\(n) nuove note"
        case .payments: "\(n) pagamenti da fare"
        case .mentions: "Sei nominato \(n) volte in agenda"
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
            let who = accounts.count > 1 ? account.name.firstWord.pretty : nil
            // In background iOS concede pochi secondi: le richieste partono tutte insieme invece che una alla volta.
            let results = await withTaskGroup(of: (AlertKind, [News]?).self) { group in
                for alert in AlertKind.allCases where enabled.contains(alert) {
                    // Sul main actor: lo stato dell'Api resta su un solo thread, ma le attese di rete si sovrappongono.
                    group.addTask { @MainActor in (alert, try? await fetch(api, alert, account)) }
                }
                var all: [AlertKind: [News]] = [:]
                for await (alert, items) in group { if let items { all[alert] = items } }
                return all
            }
            var sound = true
            for alert in AlertKind.allCases {
                guard let items = results[alert] else { continue }
                let key = "seen-\(alert.rawValue)-\(account.key)"
                if let seen = defaults.stringArray(forKey: key).map(Set.init) {
                    let new = items.filter { !seen.contains($0.id) }
                    // Al massimo 5: se sono di più l'ultima è un riepilogo che apre l'elenco.
                    let shown = new.count > 5 ? Array(new.prefix(4)) : new
                    for n in shown {
                        await post(alert, account: account.key, who: who, id: n.id, title: n.title, body: n.body, target: n.target, sound: sound)
                        sound = false
                    }
                    if new.count > 5 {
                        await post(alert, account: account.key, who: who, id: "more", title: alert.plural(new.count),
                                   body: "E altre \(new.count - 4): tocca per vederle tutte", target: alert.target, sound: false)
                    }
                }
                // Tiene solo gli id ancora presenti, così l'elenco non cresce all'infinito.
                defaults.set(items.map(\.id), forKey: key)
            }
        }
        defaults.set(Date().timeIntervalSince1970, forKey: "lastCheck")
    }

    static func notificationId(_ alert: AlertKind, account: String, id: String) -> String { "\(alert.rawValue)-\(account)-\(id)" }

    /// Una notifica per novità, raggruppate da iOS per tipo e account; suona solo la prima di ogni controllo.
    private static func post(_ alert: AlertKind, account: String, who: String?, id: String, title: String, body: String, target: String, sound: Bool) async {
        let content = UNMutableNotificationContent()
        content.userInfo = ["target": target, "account": account]
        content.title = title
        content.subtitle = who ?? ""
        content.body = body
        content.sound = sound ? .default : nil
        content.threadIdentifier = "\(alert.rawValue)-\(account)"
        try? await UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: notificationId(alert, account: account, id: id), content: content, trigger: nil))
    }

    /// Toglie dal Centro notifiche le circolari lette nell'app.
    static func dismissNotices(account: String?, ids: [Int]) {
        guard let account else { return }
        UNUserNotificationCenter.current().removeDeliveredNotifications(
            withIdentifiers: ids.map { notificationId(.notices, account: account, id: "\($0)") } + [notificationId(.notices, account: account, id: "more")])
    }

    private static func fetch(_ api: Api, _ alert: AlertKind, _ account: Account) async throws -> [News] {
        switch alert {
        case .notices:
            return try await api.notices().filter { !$0.read }.map { n in
                let c = n.attachments.count
                let pdf = c == 0 ? "" : c == 1 ? "1 PDF allegato" : "\(c) PDF allegati"
                return News(id: "\(n.id)", title: n.title, body: [n.category.ifBlank ?? "Nuova circolare", pdf].filter { !$0.isEmpty }.joined(separator: " · "), target: "notice:\(n.id)")
            }
        case .grades:
            return try await api.grades().map { g in
                let id = g.id != 0 ? "\(g.id)" : "\(g.subject)_\(g.date)_\(g.value)"
                let details = [g.type.trimmed.capFirst, shortDate(g.date), g.counts ? "" : "non fa media", g.notes.trimmed]
                return News(id: id, title: "\(g.subject): \(g.value)", body: details.filter { !$0.isEmpty }.joined(separator: " · "), target: "grades")
            }
        case .homework:
            return try await api.agenda().map { e in
                let subject = e.subject.ifBlank ?? e.author
                return News(id: "\(e.id)", title: "\(e.kind) per \(whenLabel(e.date))" + (subject.trimmed.isEmpty ? "" : " · \(subject)"),
                            body: e.notes.tidy.ifBlank ?? "Nessuna descrizione", target: e.isTest ? "tests" : e.homework ? "homework" : "agenda")
            }
        case .didactics:
            return try await api.didactics().flatMap { t in t.folders.flatMap { f in f.contents.map {
                News(id: "\($0.id)", title: "Nuovo file da \(t.name)", body: [$0.name, f.name].filter { !$0.trimmed.isEmpty }.uniqued.joined(separator: " · "), target: "didactics")
            } } }
        case .notes:
            return try await api.notes().map { News(id: "\($0.id)", title: "\($0.type) · \($0.author)", body: $0.hidden ? longDate($0.date) : $0.text, target: "notes") }
        case .payments:
            return try await api.payments().map { News(id: $0.id, title: "Da pagare: € \($0.amount)", body: "\($0.title) · entro il \(shortDate($0.due))", target: "payments") }
        case .mentions:
            let names = Mentions.names(account)
            let today = Day.iso(Date())
            return try await api.agenda().filter { $0.date >= today && Mentions.matches("\($0.notes) \($0.subject)", names) }.map { e in
                let subject = e.subject.ifBlank ?? e.author
                return News(id: "m\(e.id)", title: "Sei nominato: \(e.kind) per \(whenLabel(e.date))" + (subject.trimmed.isEmpty ? "" : " · \(subject)"),
                            body: e.notes.tidy, target: e.isTest ? "tests" : "day")
            }
        }
    }
}

/// Una novità da notificare: [target] è la pagina che si apre toccandola.
struct News {
    var id: String, title: String, body: String, target: String
}

/// "oggi", "domani" o la data per esteso, da mettere dopo "per".
func whenLabel(_ s: String, today: Date = Date()) -> String {
    guard let d = Day.parse(s) else { return s }
    let start = Day.cal.startOfDay(for: today)
    switch Day.between(start, d) {
    case 0: return "oggi"
    case 1: return "domani"
    default: return Day.longFmt.string(from: d)
    }
}

private extension Array where Element == String {
    var uniqued: [String] { var seen = Set<String>(); return filter { seen.insert($0).inserted } }
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

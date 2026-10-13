import SwiftUI
import UserNotifications

@main
struct CVNextApp: App {
    @Environment(\.scenePhase) private var phase
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate

    var body: some Scene {
        WindowGroup { RootView() }
            .onChange(of: phase) { _, p in
                if p == .background { Background.schedule() }
            }
            .backgroundTask(.appRefresh(Background.id)) { await Background.run() }
    }
}

/// Tocco su una notifica e scorciatoie dell'icona dell'app: dicono al Router dove andare.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(_ application: UIApplication, configurationForConnecting session: UISceneSession, options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        if let item = options.shortcutItem { Task { @MainActor in Router.shared.target = item.type } }
        let config = UISceneConfiguration(name: nil, sessionRole: session.role)
        config.delegateClass = SceneDelegate.self
        return config
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let info = response.notification.request.content.userInfo
        let target = info["target"] as? String
        let account = info["account"] as? String
        await MainActor.run {
            Router.shared.account = account
            Router.shared.target = target
        }
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }
}

final class SceneDelegate: NSObject, UIWindowSceneDelegate {
    func windowScene(_ windowScene: UIWindowScene, performActionFor shortcutItem: UIApplicationShortcutItem) async -> Bool {
        await MainActor.run { Router.shared.account = nil; Router.shared.target = shortcutItem.type }
        return true
    }
}

struct RootView: View {
    @AppStorage("mode") private var mode = 0
    @AppStorage("tint") private var tint = 0
    @AppStorage("lock") private var lock = false
    @State private var current: String? = Api().current
    @State private var adding = false
    @State private var locked = UserDefaults.standard.bool(forKey: "lock") && UserDefaults.standard.string(forKey: "debugScreen") == nil && AppLock.available
    @State private var leftAt: Date?
    @Environment(\.scenePhase) private var phase
    private let debug = UserDefaults.standard.string(forKey: "debugScreen")

    var body: some View {
        content
            .overlay {
                if locked {
                    LockView { Task { if await AppLock.unlock() { withAnimation { locked = false } } } }
                        .environment(\.appTint, Palette.color(tint))
                        .tint(Palette.color(tint))
                }
            }
            .onChange(of: phase) { _, p in
                if p == .background { leftAt = Date() }
                if p == .active, let t = leftAt {
                    leftAt = nil
                    if lock, debug == nil, Date().timeIntervalSince(t) > AppLock.grace, AppLock.available { locked = true }
                }
            }
            // Da una notifica di un altro account: prima si passa a quell'account.
            .onChange(of: Router.shared.account) { _, key in
                if let key, key != current, Api().accounts.contains(where: { $0.key == key }) {
                    Api().switchTo(key)
                    current = key
                }
            }
    }

    @ViewBuilder private var content: some View {
        Group {
            if let debug {
                DebugRoot(screen: debug)
            } else if current == nil || adding {
                LoginView(onBack: current != nil ? { adding = false } : nil) {
                    adding = false
                    current = Api().current
                }
                .transition(.opacity.combined(with: .scale(scale: 0.95)))
            } else {
                HomeView(
                    model: Model(api: Api()),
                    onSwitch: { key in Api().switchTo(key); current = key },
                    onAdd: { adding = true },
                    onLogout: { Api().logout(); current = Api().current }
                )
                .id(current)
                .transition(.opacity)
            }
        }
        .animation(.smooth, value: current)
        .animation(.smooth, value: adding)
        .environment(\.appTint, Palette.color(tint))
        .tint(Palette.color(tint))
        .preferredColorScheme(mode == 1 ? .light : mode == 2 ? .dark : nil)
    }
}

/// Apre direttamente una schermata con dati di esempio, per gli screenshot automatici.
struct DebugRoot: View {
    let screen: String
    @State private var model = Model(debug: ())

    var body: some View {
        switch screen {
        case "login": LoginView(onBack: nil) {}
        case "loginAdd": LoginView(onBack: {}) {}
        case "settings": NavigationStack { SettingsView() }.environment(model)
        case "absences": NavigationStack { AbsencesView().navigationTitle("Assenze") }.environment(model)
        case "agenda": NavigationStack { AgendaView(filter: "Tutto").navigationTitle("Agenda") }.environment(model)
        case "subject":
            NavigationStack { SubjectView(subject: "Matematica") }.environment(model)
        case "tests": NavigationStack { TestsView() }.environment(model)
        default:
            HomeView(model: model, onSwitch: { _ in }, onAdd: {}, onLogout: {})
                .onAppear {
                    switch screen {
                    case "day": model.tab = .day
                    case "grades": model.tab = .grades
                    case "notices": model.tab = .notices
                    case "functions": model.tab = .functions
                    case "invite": model.tab = .today; model.showInvite = true
                    default: model.tab = .today
                    }
                }
        }
    }
}

enum Page: Hashable {
    case absences, notes, didactics, documents, lessons, settings
    case agenda(String)
    case notice(Notice)
    case web(String, String)
    case subject(String)
    case tests
}

struct HomeView: View {
    @State var model: Model
    let onSwitch: (String) -> Void
    let onAdd: () -> Void
    let onLogout: () -> Void
    @Environment(\.scenePhase) private var phase

    var body: some View {
        @Bindable var model = model
        TabView(selection: $model.tab) {
            Tab("Home", systemImage: "house", value: AppTab.today) {
                NavigationStack(path: $model.todayPath) { TodayView().chrome("Home") }
            }
            Tab("Giornata", systemImage: "calendar", value: AppTab.day) {
                NavigationStack { DayView().chrome("Giornata") }
            }
            // Numeri sulle schede: voti nuovi e circolari da leggere.
            Tab("Voti", systemImage: "star", value: AppTab.grades) {
                NavigationStack { GradesView().chrome("Voti") }
            }
            .badge(model.freshGrades.count)
            Tab("Bacheca", systemImage: "megaphone", value: AppTab.notices) {
                NavigationStack(path: $model.noticePath) { NoticesView().chrome("Bacheca") }
            }
            .badge((model.notices.data ?? []).filter { !$0.read }.count)
            Tab("Funzioni", systemImage: "square.grid.2x2", value: AppTab.functions) {
                NavigationStack { FunctionsView().chrome("Funzioni") }
            }
        }
        // Su iPad le schede diventano una barra laterale che si può aprire e chiudere.
        .tabViewStyle(.sidebarAdaptable)
        // Barra sempre intera: non si rimpicciolisce scorrendo.
        .tabBarMinimizeBehavior(.never)
        .environment(model)
        .sheet(isPresented: $model.showAccount) {
            AccountSheet(onSwitch: onSwitch, onAdd: onAdd, onLogout: onLogout).environment(model)
        }
        // Invito ad attivare le notifiche, se manca il permesso o l'aggiornamento in background.
        .sheet(isPresented: $model.showInvite) { InviteSheet() }
        .task {
            await model.loadAll()
            guard model.debugName == nil else { return }
            await checkInvite()
            model.update = await Updater.dailyCheck()
            Background.schedule()
            await Evening.reschedule()
        }
        .onChange(of: phase) { _, p in
            if p == .active { Task { await checkInvite() } }
        }
        // Uscendo dalla scheda Voti quelli nuovi diventano visti.
        .onChange(of: model.tab) { old, _ in
            if old == .grades, let g = model.grades.data { model.seen.markSeen(g) }
        }
        .task(id: router.target) { await route() }
        .onChange(of: model.notices.data) { _, _ in Task { await route() } }
    }
}

extension HomeView {
    private var router: Router { Router.shared }

    /// Apre la pagina chiesta da una notifica o da una scorciatoia dell'icona.
    func route() async {
        guard let t = router.target, router.account == nil || router.account == model.api.current else { return }
        if t.hasPrefix("notice:") {
            model.tab = .notices
            let id = String(t.dropFirst("notice:".count))
            if let n = (model.notices.data ?? []).first(where: { "\($0.id)" == id }) {
                router.target = nil
                model.noticePath = NavigationPath([Page.notice(n)])
            } else if !model.notices.loading {
                // Non c'è ancora: ricarica la Bacheca una volta, poi lascia perdere.
                router.target = nil
                await model.notices.refresh()
                if let n = (model.notices.data ?? []).first(where: { "\($0.id)" == id }) {
                    model.noticePath = NavigationPath([Page.notice(n)])
                }
            }
            return
        }
        router.target = nil
        switch t {
        case "grades": model.tab = .grades
        case "day": model.tab = .day
        case "notices": model.tab = .notices
        case "homework": model.tab = .today; model.todayPath = NavigationPath([Page.agenda("Compiti")])
        case "tests": model.tab = .today; model.todayPath = NavigationPath([Page.tests])
        default: break
        }
    }
}

extension HomeView {
    /// Propone l'invito solo se non c'è già un'altra finestra aperta.
    func checkInvite() async {
        guard model.debugName == nil, !model.showInvite, !model.showAccount, await Invite.due() else { return }
        model.showInvite = true
    }
}

private struct Chrome: ViewModifier {
    let title: String
    @Environment(Model.self) private var model

    func body(content: Content) -> some View {
        content
            .navigationTitle(title)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    NavigationLink(value: Page.settings) { Image(systemName: "gearshape") }
                }
                ToolbarSpacer(.fixed, placement: .topBarTrailing)
                ToolbarItem(placement: .topBarTrailing) {
                    Button { model.showAccount = true } label: { Avatar(name: model.name, size: 30) }
                        .accessibilityLabel("Account e profilo")
                }
            }
            .navigationDestination(for: Page.self) { PageView(page: $0) }
    }
}

extension View {
    func chrome(_ title: String) -> some View { modifier(Chrome(title: title)) }
}

struct PageView: View {
    let page: Page
    var body: some View {
        switch page {
        case .absences: AbsencesView().navigationTitle("Assenze")
        case .notes: NotesView().navigationTitle("Note")
        case .didactics: DidacticsView().navigationTitle("File dei docenti")
        case .documents: DocumentsView().navigationTitle("Scrutini")
        case .lessons: LessonsView().navigationTitle("Lezioni")
        case .settings: SettingsView()
        case .agenda(let filter): AgendaView(filter: filter).navigationTitle("Agenda")
        case .notice(let n): NoticeView(notice: n)
        case .web(let title, let url): WebScreen(title: title, url: url)
        case .subject(let s): SubjectView(subject: s)
        case .tests: TestsView()
        }
    }
}

struct AccountSheet: View {
    let onSwitch: (String) -> Void
    let onAdd: () -> Void
    let onLogout: () -> Void
    @Environment(Model.self) private var model
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack(spacing: 16) {
                        Avatar(name: model.name, size: 60)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(model.name.pretty).font(.title3.weight(.semibold))
                            Text(model.api.uid).foregroundStyle(.subtle)
                            if let s = model.school.data {
                                Text([s.name, s.city].filter { !$0.isEmpty }.joined(separator: " · ")).font(.footnote).foregroundStyle(.tint)
                            }
                        }
                    }
                    .padding(.vertical, 6)
                }
                Section("Account") {
                    ForEach(model.api.accounts) { a in
                        let active = a.key == model.api.current
                        Button {
                            dismiss()
                            onSwitch(a.key)
                        } label: {
                            HStack(spacing: 12) {
                                Avatar(name: a.name)
                                VStack(alignment: .leading) {
                                    Text(a.name.pretty).foregroundStyle(.primary)
                                    Text(active ? "In uso" : "Tocca per passare a questo account").font(.caption).foregroundStyle(.subtle)
                                }
                                Spacer()
                                if active { Image(systemName: "checkmark").foregroundStyle(.tint) }
                            }
                        }
                        .disabled(active)
                    }
                    Button { dismiss(); onAdd() } label: { Label("Aggiungi account", systemImage: "person.badge.plus") }
                }
                Section {
                    Button(role: .destructive) { dismiss(); onLogout() } label: {
                        Label("Esci da questo account", systemImage: "rectangle.portrait.and.arrow.right")
                    }
                }
            }
            .navigationTitle("Profilo")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Chiudi") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

/// Copre l'app finché non si sblocca con Face ID, Touch ID o codice.
struct LockView: View {
    let unlock: () -> Void
    var body: some View {
        VStack(spacing: 18) {
            Image(systemName: "lock.fill").font(.system(size: 52)).foregroundStyle(.tint)
            Text("CV Next è bloccata").font(.title2.weight(.semibold))
            Button(action: unlock) { Label("Sblocca", systemImage: AppLock.biometryName == "Face ID" ? "faceid" : "touchid") }
                .buttonStyle(.glassProminent)
                .controlSize(.large)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background { AppBackground().ignoresSafeArea() }
        .background(.background)
        .onAppear(perform: unlock)
    }
}

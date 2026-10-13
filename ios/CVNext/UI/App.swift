import SwiftUI

@main
struct CVNextApp: App {
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup { RootView() }
            .onChange(of: phase) { _, p in
                if p == .background { Background.schedule() }
            }
            .backgroundTask(.appRefresh(Background.id)) { await Background.run() }
    }
}

struct RootView: View {
    @AppStorage("mode") private var mode = 0
    @AppStorage("tint") private var tint = 0
    @State private var current: String? = Api().current
    @State private var adding = false
    private let debug = UserDefaults.standard.string(forKey: "debugScreen")

    var body: some View {
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
                NavigationStack { TodayView().chrome("Home") }
            }
            Tab("Giornata", systemImage: "calendar", value: AppTab.day) {
                NavigationStack { DayView().chrome("Giornata") }
            }
            Tab("Voti", systemImage: "star", value: AppTab.grades) {
                NavigationStack { GradesView().chrome("Voti") }
            }
            Tab("Bacheca", systemImage: "megaphone", value: AppTab.notices) {
                NavigationStack { NoticesView().chrome("Bacheca") }
            }
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

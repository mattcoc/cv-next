import SwiftUI
import UserNotifications

/// Quando proporre l'invito ad attivare le notifiche: al massimo tre volte, a quattro giorni di distanza,
/// e mai a chi le ha spente apposta nelle Impostazioni.
@MainActor
enum Invite {
    private static let limit = 3
    private static let pause: TimeInterval = 4 * 24 * 3600
    private static var defaults: UserDefaults { .standard }

    /// Vero se l'utente ha spento tutte le notifiche dalle Impostazioni dell'app.
    static var optedOut: Bool { defaults.bool(forKey: "alertsOff") && AlertKind.enabled.isEmpty }

    static func authorization() async -> UNAuthorizationStatus {
        await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
    }

    static func allowed(_ status: UNAuthorizationStatus) -> Bool {
        status == .authorized || status == .provisional || status == .ephemeral
    }

    /// Notifiche permesse, almeno un tipo scelto e aggiornamento in background attivo.
    static func ready() async -> Bool {
        let status = await authorization()
        return !AlertKind.enabled.isEmpty && allowed(status) && UIApplication.shared.backgroundRefreshStatus == .available
    }

    static func due() async -> Bool {
        guard !optedOut, defaults.integer(forKey: "inviteCount") < limit,
              Date().timeIntervalSince1970 >= defaults.double(forKey: "inviteNext") else { return false }
        let ok = await ready()
        return !ok
    }

    /// L'invito è stato chiuso senza finire: si riproporrà più avanti.
    static func later() {
        defaults.set(defaults.integer(forKey: "inviteCount") + 1, forKey: "inviteCount")
        defaults.set(Date().timeIntervalSince1970 + pause, forKey: "inviteNext")
    }

    /// Accende tutti i tipi di notifica e programma i controlli in background.
    static func enableAlerts() {
        if AlertKind.enabled.isEmpty { AlertKind.enabled = Set(AlertKind.allCases) }
        defaults.removeObject(forKey: "alertsOff")
        Background.schedule()
    }
}

/// Invito ad attivare le notifiche, passo per passo: permesso di iOS e aggiornamento in background.
struct InviteSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var phase
    @Environment(\.openURL) private var openURL
    @Environment(\.appTint) private var tint
    @State private var status: UNAuthorizationStatus = .notDetermined
    @State private var alerts = !AlertKind.enabled.isEmpty
    @State private var refresh = UIApplication.shared.backgroundRefreshStatus
    @State private var height: CGFloat = 560

    private var on: Bool { alerts && Invite.allowed(status) }
    private var background: Bool { refresh == .available }
    /// 0: mancano le notifiche, 1: manca l'aggiornamento in background, 2: tutto pronto.
    private var step: Int { !on ? 0 : !background ? 1 : 2 }

    var body: some View {
        ScrollView {
            card
                .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { height = $0 + 20 }
        }
        .scrollBounceBehavior(.basedOnSize)
        .presentationDetents([.height(height)])
        .animation(.smooth, value: step)
        .task { await reload() }
        // Le scelte si fanno nelle Impostazioni di iOS: al ritorno si rilegge lo stato.
        .onChange(of: phase) { _, p in if p == .active { Task { await reload() } } }
        .onDisappear { if step < 2 { Invite.later() } }
    }

    private var card: some View {
        VStack(spacing: 0) {
            Image(systemName: step == 2 ? "checkmark" : "bell.badge.fill")
                .font(.system(size: 30, weight: .semibold))
                .foregroundStyle(tint)
                .contentTransition(.symbolEffect(.replace))
                .frame(width: 72, height: 72)
                .glassEffect(.regular.tint(tint.opacity(0.25)), in: .circle)
            Text(step == 2 ? "Tutto pronto" : "Non perderti voti e verifiche")
                .font(.title2.bold())
                .multilineTextAlignment(.center)
                .padding(.top, 16)
            Text(step == 2
                 ? "Ti avviseremo quando arriva qualcosa di nuovo, anche con l'app chiusa. Puoi scegliere cosa ricevere in Impostazioni → Notifiche."
                 : "CV Next ti avvisa quando arriva un voto, una verifica, un compito o una circolare, anche con l'app chiusa.")
                .font(.subheadline)
                .foregroundStyle(.subtle)
                .multilineTextAlignment(.center)
                .padding(.top, 8)
            VStack(alignment: .leading, spacing: 16) {
                InviteStep(number: 1, icon: "bell.badge", title: "Consenti le notifiche",
                           text: status == .denied
                               ? "iOS le ha bloccate: tocca il pulsante e attiva \"Consenti notifiche\""
                               : "Scegli tu cosa ricevere: voti, agenda, bacheca e quando sei tra gli interrogati",
                           done: on, current: step == 0)
                InviteStep(number: 2, icon: "arrow.clockwise", title: "Aggiornamento in background",
                           text: "Attiva \"Aggiorna app in background\" per CV Next: iOS controlla qualche volta al giorno, la batteria quasi non se ne accorge",
                           done: background, current: step == 1)
            }
            .padding(.top, 24)
            Button(action: primary) {
                Text(step == 0 ? (status == .denied ? "Apri Impostazioni" : "Attiva le notifiche") : step == 1 ? "Apri Impostazioni" : "Fatto")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.glassProminent)
            .controlSize(.large)
            .padding(.top, 28)
            if step < 2 {
                Button("Non ora") { dismiss() }
                    .font(.subheadline.weight(.semibold))
                    .padding(.top, 14)
            }
        }
        .padding(.horizontal, 24)
        .padding(.top, 32)
        .padding(.bottom, 16)
        .frame(maxWidth: 520)
        .frame(maxWidth: .infinity)
    }

    private func reload() async {
        status = await Invite.authorization()
        alerts = !AlertKind.enabled.isEmpty
        refresh = UIApplication.shared.backgroundRefreshStatus
    }

    private func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
    }

    private func primary() {
        switch step {
        case 0:
            Task {
                switch status {
                case .notDetermined:
                    if await Background.requestPermission() { Invite.enableAlerts() }
                case .denied:
                    // Il permesso negato si può ridare solo dalle Impostazioni di iOS.
                    Invite.enableAlerts()
                    openSettings()
                default:
                    Invite.enableAlerts()
                }
                await reload()
            }
        case 1: openSettings()
        default: dismiss()
        }
    }
}

private struct InviteStep: View {
    let number: Int
    let icon: String
    let title: String
    let text: String
    let done: Bool
    let current: Bool
    @Environment(\.appTint) private var tint

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: done ? "checkmark" : icon)
                .font(.body.weight(.semibold))
                .foregroundStyle(done ? Color.white : current ? tint : Color.secondary)
                .contentTransition(.symbolEffect(.replace))
                .frame(width: 44, height: 44)
                .background(done ? AnyShapeStyle(tint.gradient) : AnyShapeStyle((current ? tint : Color.secondary).opacity(0.16)), in: .circle)
                .accessibilityLabel(done ? "Fatto" : "Passo \(number)")
            VStack(alignment: .leading, spacing: 2) {
                Text("\(number). \(title)").font(.subheadline.weight(.semibold))
                    .foregroundStyle(done || current ? Color.primary : Color.secondary)
                Text(text).font(.caption).foregroundStyle(.subtle)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
    }
}

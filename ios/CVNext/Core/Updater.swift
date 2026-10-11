import Foundation
import SwiftUI

/// Su iOS un'app non può installare altre app: l'aggiornamento si passa a SideStore (o AltStore),
/// che scarica la nuova IPA dalla pre-release `ios-latest` e la installa sopra quella attuale.
struct IosRelease: Equatable {
    let build: Int
    let date: Date?
    let ipa: URL
}

enum Updater {
    static let api = URL(string: "https://api.github.com/repos/mattcoc/cv-next/releases/tags/ios-latest")!
    static let page = URL(string: "\(Endpoints.repo)/releases/tag/ios-latest")!

    static var installedBuild: Int { Int(Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "") ?? 0 }
    static var installedVersion: String {
        let v = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
        return "\(v) (build \(installedBuild))"
    }

    /// La build è scritta nelle note della release ("Build 42").
    static func latest() async throws -> IosRelease {
        var req = URLRequest(url: api)
        req.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        let (data, response) = try await URLSession.shared.data(for: req)
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard code == 200, let json = (try? JSONSerialization.jsonObject(with: data)) as? JSON else {
            throw ApiError("Impossibile controllare gli aggiornamenti (errore \(code))")
        }
        guard let asset = json.list("assets").first(where: { $0.str("name").lowercased().hasSuffix(".ipa") }),
              let url = URL(string: asset.str("browser_download_url")) else {
            throw ApiError("L'ultima versione non contiene un'IPA")
        }
        let build = Rx("Build (\\d+)").first(json.str("body")).map { Int((json.str("body") as NSString).substring(with: $0.range(at: 1))) ?? 0 } ?? 0
        return IosRelease(build: build, date: ISO8601DateFormatter().date(from: asset.str("updated_at")), ipa: url)
    }

    /// Link che apre SideStore (o AltStore) e installa l'IPA.
    static func installLinks(_ r: IosRelease) -> [URL] {
        let encoded = r.ipa.absoluteString.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? r.ipa.absoluteString
        return ["sidestore", "altstore"].compactMap { URL(string: "\($0)://install?url=\(encoded)") }
    }

    /// Controllo automatico all'avvio, al massimo una volta al giorno.
    static func dailyCheck() async -> IosRelease? {
        let defaults = UserDefaults.standard
        guard defaults.object(forKey: "autoUpdate") as? Bool ?? true else { return nil }
        if Date().timeIntervalSince1970 - defaults.double(forKey: "updateChecked") < 24 * 3600 { return nil }
        guard let r = try? await latest() else { return nil }
        defaults.set(Date().timeIntervalSince1970, forKey: "updateChecked")
        return r.build > installedBuild ? r : nil
    }
}

/// Prova SideStore, poi AltStore; se nessuno dei due è installato scarica l'IPA con Safari.
@MainActor
func installUpdate(_ r: IosRelease, _ openURL: OpenURLAction, links: [URL]? = nil) {
    var rest = links ?? Updater.installLinks(r)
    guard !rest.isEmpty else { openURL(r.ipa); return }
    let next = rest.removeFirst()
    openURL(next) { accepted in
        if !accepted { Task { @MainActor in installUpdate(r, openURL, links: rest) } }
    }
}

/// Avviso di nuova versione, in cima a Oggi.
struct UpdateBanner: View {
    let release: IosRelease
    let onClose: () -> Void
    @Environment(\.openURL) private var openURL
    @Environment(\.appTint) private var tint

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "arrow.down.app.fill").font(.title2).foregroundStyle(tint)
            VStack(alignment: .leading, spacing: 2) {
                Text("Nuova versione disponibile").fontWeight(.semibold)
                Text("Tocca per aggiornare con SideStore").font(.caption).foregroundStyle(.subtle)
            }
            Spacer()
            Button(action: onClose) { Image(systemName: "xmark").font(.subheadline.weight(.semibold)) }
                .buttonStyle(.glass)
                .buttonBorderShape(.circle)
                .accessibilityLabel("Ignora")
        }
        .contentShape(.rect)
        .onTapGesture { installUpdate(release, openURL) }
        .glassCard(tint, interactive: true)
    }
}

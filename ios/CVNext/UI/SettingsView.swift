import SwiftUI

struct SettingsView: View {
    @Environment(Model.self) private var model
    @AppStorage("mode") private var mode = 0
    @AppStorage("tint") private var tint = 0
    @AppStorage("alerts") private var alertsRaw = ""
    @AppStorage("evening") private var evening = false
    @AppStorage("eveningHour") private var eveningHour = 19
    @AppStorage("lastCheck") private var lastCheck: Double = 0
    @State private var names = ""
    @State private var checking = false
    @State private var cleared = false

    private var alerts: Set<AlertKind> {
        Set(alertsRaw.split(separator: ",").compactMap { AlertKind(rawValue: String($0)) })
    }

    private func setAlerts(_ set: Set<AlertKind>) {
        alertsRaw = set.map(\.rawValue).sorted().joined(separator: ",")
        if !set.isEmpty { Task { _ = await Background.requestPermission(); Background.schedule() } }
    }

    var body: some View {
        Form {
            Section("Aspetto") {
                Picker("Tema", selection: $mode) {
                    Text("Sistema").tag(0)
                    Text("Chiaro").tag(1)
                    Text("Scuro").tag(2)
                }
                .pickerStyle(.segmented)
                VStack(alignment: .leading, spacing: 10) {
                    Text("Colore")
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 5), spacing: 12) {
                        ForEach(Palette.options.indices, id: \.self) { i in
                            Button { tint = i } label: {
                                Circle().fill(Palette.options[i].1.gradient)
                                    .frame(width: 40, height: 40)
                                    .overlay { if tint == i { Image(systemName: "checkmark").font(.headline).foregroundStyle(.white) } }
                                    .padding(3)
                                    .overlay { if tint == i { Circle().stroke(Palette.options[i].1, lineWidth: 2) } }
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(Palette.options[i].0)
                        }
                    }
                }
                .padding(.vertical, 4)
            }

            Section {
                Toggle(isOn: Binding(get: { !alerts.isEmpty }, set: { setAlerts($0 ? Set(AlertKind.allCases) : []) })) {
                    Label("Notifiche", systemImage: "bell.badge")
                }
                ForEach(AlertKind.allCases) { a in
                    Toggle(isOn: Binding(get: { alerts.contains(a) }, set: { on in
                        var s = alerts
                        if on { s.insert(a) } else { s.remove(a) }
                        setAlerts(s)
                    })) {
                        Label { VStack(alignment: .leading) { Text(a.title); Text(a.detail).font(.caption).foregroundStyle(.subtle) } } icon: { Image(systemName: a.icon) }
                    }
                }
                Button {
                    checking = true
                    Task { await Checker.run(); checking = false }
                } label: {
                    HStack { Text("Controlla adesso"); Spacer(); if checking { ProgressView() } }
                }
                .disabled(checking || alerts.isEmpty)
            } header: {
                Text("Notifiche")
            } footer: {
                Text(lastCheck > 0
                     ? "Ultimo controllo: \(Date(timeIntervalSince1970: lastCheck).formatted(date: .abbreviated, time: .shortened)). Su iOS è il sistema a decidere quando controllare: di solito qualche volta al giorno."
                     : "Su iOS è il sistema a decidere quando controllare: di solito qualche volta al giorno, più spesso se usi l'app.")
            }

            Section {
                Toggle(isOn: Binding(get: { evening }, set: { on in
                    evening = on
                    Task {
                        if on { _ = await Background.requestPermission() }
                        await Evening.reschedule()
                    }
                })) {
                    Label { VStack(alignment: .leading) { Text("Promemoria della sera"); Text("Compiti, verifiche e promemoria del giorno dopo").font(.caption).foregroundStyle(.subtle) } } icon: { Image(systemName: "moon.stars") }
                }
                if evening {
                    Picker("Ora", selection: Binding(get: { eveningHour }, set: { h in eveningHour = h; Task { await Evening.reschedule() } })) {
                        ForEach(17...22, id: \.self) { Text("\($0):00").tag($0) }
                    }
                }
            } footer: {
                Text("Viene preparato con i dati dell'ultima apertura dell'app o dell'ultimo controllo.")
            }

            if let account = model.account {
                Section {
                    TextField("Nomi", text: $names)
                        .autocorrectionDisabled()
                        .onSubmit { Mentions.save(account, names) }
                        .onAppear { names = Mentions.names(account).joined(separator: ", ") }
                        .onDisappear { Mentions.save(account, names) }
                    Button("Ripristina") { names = Mentions.defaultNames(account.name).joined(separator: ", "); Mentions.save(account, names) }
                } header: {
                    Text("Il tuo nome in agenda")
                } footer: {
                    Text("Separati da virgola. Servono per \"Ci sei anche tu\" e per la notifica quando sei nominato, ad esempio tra gli interrogati.")
                }
            }

            Section("Dati") {
                Button("Svuota dati offline e file scaricati") {
                    let fm = FileManager.default
                    try? fm.removeItem(at: Api.downloads)
                    if let support = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first {
                        try? fm.removeItem(at: support.appendingPathComponent("api"))
                    }
                    cleared = true
                }
                .disabled(cleared)
            }

            Section {
                LabeledContent("Versione", value: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "")
                if let url = URL(string: Endpoints.repo) { Link("Codice sorgente su GitHub", destination: url) }
            } footer: {
                Text("Progetto non ufficiale. Non è affiliato, sponsorizzato o approvato da Gruppo Spaggiari Parma S.p.A. \"ClasseViva\" è un marchio registrato di Gruppo Spaggiari Parma S.p.A.")
            }
        }
        .scrollContentBackground(.hidden)
        .background { AppBackground() }
        .navigationTitle("Impostazioni")
    }
}

import SwiftUI
import WebKit
import QuickLook

struct FunctionsView: View {
    @Environment(Model.self) private var model

    private struct Fn: Identifiable {
        let label: String, icon: String, color: Color
        var tab: AppTab? = nil
        var page: Page? = nil
        var id: String { label }
    }

    private let school: [Fn] = [
        Fn(label: "Valutazioni", icon: "star.fill", color: .yellow, tab: .grades),
        Fn(label: "Bacheca", icon: "megaphone.fill", color: .orange, tab: .notices),
        Fn(label: "Scrutini", icon: "chart.bar.doc.horizontal", color: .indigo, page: .documents),
        Fn(label: "Assenze", icon: "calendar.badge.minus", color: .red, page: .absences),
        Fn(label: "Note", icon: "exclamationmark.bubble.fill", color: .pink, page: .notes),
        Fn(label: "Agenda", icon: "calendar", color: .teal, page: .agenda("Tutto")),
        Fn(label: "Lezioni", icon: "person.crop.rectangle.stack", color: .blue, page: .lessons),
        Fn(label: "File dei docenti", icon: "folder.fill", color: .cyan, page: .didactics),
        Fn(label: "Compiti", icon: "pencil.and.list.clipboard", color: .green, page: .agenda("Compiti"))
    ]

    private let web: [Fn] = [
        Fn(label: "Colloqui", icon: "person.2.fill", color: .purple, page: .web("Colloqui", WebLinks.meetings)),
        Fn(label: "Sportello", icon: "questionmark.bubble.fill", color: .mint, page: .web("Sportello", WebLinks.desk)),
        Fn(label: "PagOnline", icon: "creditcard.fill", color: .orange, page: .web("PagOnline", WebLinks.payments)),
        Fn(label: "Progettiamo la Scuola", icon: "book.fill", color: .brown, page: .web("Progettiamo la Scuola", WebLinks.projects)),
        Fn(label: "Certificazioni", icon: "rosette", color: .yellow, page: .web("Certificazioni", WebLinks.certificates)),
        Fn(label: "Registro Web", icon: "globe", color: .blue, page: .web("Registro Web", WebLinks.home)),
        Fn(label: "Anno precedente", icon: "clock.arrow.circlepath", color: .gray, page: .web("Anno precedente", WebLinks.lastYear))
    ]

    var body: some View {
        Screen {
            Header(text: "Scuola")
            grid(school)
            Header(text: "Servizi web")
            grid(web)
        }
    }

    private func grid(_ fns: [Fn]) -> some View {
        LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
            ForEach(fns) { fn in
                let tile = VStack(alignment: .leading, spacing: 10) {
                    Image(systemName: fn.icon).font(.title3).foregroundStyle(fn.color)
                        .frame(width: 40, height: 40)
                        .background(fn.color.opacity(0.18), in: .circle)
                    Text(fn.label).font(.subheadline.weight(.semibold)).lineLimit(2, reservesSpace: true).multilineTextAlignment(.leading)
                }
                .foregroundStyle(.primary)
                .glassCard(interactive: true)
                if let tab = fn.tab {
                    Button { model.tab = tab } label: { tile }.buttonStyle(.plain)
                } else if let page = fn.page {
                    NavigationLink(value: page) { tile }.buttonStyle(.plain)
                }
            }
        }
    }
}

struct AbsencesView: View {
    @Environment(Model.self) private var model

    var body: some View {
        Screen(refresh: { await model.refresh([model.absences]) }) {
            Loaded(model.absences) { content }
        }
    }

    @ViewBuilder private var content: some View {
        let list = model.absences.data ?? []
        let kinds = [("Assenze", "Assenza"), ("Ritardi", "Ritardo"), ("Assenze parziali", "Assenza parziale"), ("Uscite anticipate", "Uscita anticipata")]
        if list.isEmpty {
            VStack(spacing: 8) {
                Image(systemName: "checkmark.seal.fill").font(.system(size: 44))
                Text("Presenza perfetta").font(.title2.bold())
                Text("Quest'anno nessuna assenza, ritardo o uscita anticipata.").multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .glassCard(.green, padding: 28)
        } else {
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                ForEach(kinds.indices, id: \.self) { i in
                    let label = kinds[i].0
                    let type = kinds[i].1
                    let of = list.filter { $0.type == type }
                    let open = of.filter { !$0.justified }.count
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(of.count)").font(.system(size: 34, weight: .bold, design: .rounded))
                        Text(label).font(.subheadline.weight(.semibold))
                        Text(of.isEmpty ? "Nessuna" : open == 0 ? (of.count == 1 ? "Giustificata" : "Tutte giustificate") : "\(open) da giustificare").font(.caption)
                    }
                    .glassCard(absenceStyle(type).1)
                }
            }
        }
        let months = Array(Set(list.map { String($0.date.prefix(7)) })).sorted(by: >)
        ForEach(months, id: \.self) { month in
            Header(text: monthName(month))
            ForEach(Array(list.filter { $0.date.hasPrefix(month) }.enumerated()), id: \.offset) { _, a in
                let style = absenceStyle(a.type)
                ItemRow(
                    headline: a.type, overline: longDate(a.date),
                    supporting: a.justified ? ["Giustificata", a.reason].filter { !$0.trimmed.isEmpty }.joined(separator: " · ") : "Da giustificare",
                    leading: AnyView(Badge(text: style.0, color: style.1)),
                    trailing: a.justified ? nil : AnyView(Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.red))
                )
            }
        }
    }
}

struct NotesView: View {
    @Environment(Model.self) private var model

    var body: some View {
        Screen(refresh: { await model.refresh([model.notes]) }) {
            Loaded(model.notes) {
                let list = model.notes.data ?? []
                if list.isEmpty { EmptyCard(text: "Nessuna nota. Ottimo così!", icon: "hand.thumbsup") }
                ForEach(Array(list.enumerated()), id: \.offset) { _, n in
                    let color: Color = n.type == "Nota disciplinare" || n.type == "Sanzione disciplinare" ? .red : n.type == "Richiamo" ? .orange : .blue
                    ItemRow(headline: n.type, overline: [longDate(n.date), n.author].filter { !$0.trimmed.isEmpty }.joined(separator: " · "),
                            supporting: n.text, leading: AnyView(IconBadge(icon: "exclamationmark.bubble.fill", color: color)))
                }
            }
        }
        .task { await model.notes.ensure() }
    }
}

struct LessonsView: View {
    @Environment(Model.self) private var model
    private let days: [Date] = (-30...0).map { Day.add(Day.today, $0) }.filter { !Day.isSunday($0) }
    @State private var selected = Day.isSunday(Day.today) ? Day.add(Day.today, -1) : Day.today

    var body: some View {
        Screen(refresh: { await model.refresh([model.lessons]) }) {
            Loaded(model.lessons) {
                let lessons = model.lessons.data ?? []
                DayStrip(days: days, selected: $selected) { d in lessons.contains { $0.date == Day.iso(d) } }
                VStack(alignment: .leading, spacing: 10) {
                    let list = lessons.filter { $0.date == Day.iso(selected) }
                    DayLabel(day: selected)
                    if list.isEmpty { EmptyCard(text: "Nessuna lezione registrata") }
                    ForEach(Array(list.enumerated()), id: \.offset) { _, l in LessonRow(lesson: l) }
                }
                .id(selected)
                .daySwipe(days, $selected)
            }
        }
    }
}

struct DidacticsView: View {
    @Environment(Model.self) private var model
    @Environment(\.openURL) private var openURL
    @State private var expanded: Set<String> = []
    @State private var opening: Int?
    @State private var preview: URL?
    @State private var text: String?

    var body: some View {
        Screen(refresh: { await model.refresh([model.didactics]) }) {
            Loaded(model.didactics) {
                let teachers = model.didactics.data ?? []
                if teachers.isEmpty { EmptyCard(text: "I docenti non hanno ancora condiviso file", icon: "folder") }
                ForEach(teachers, id: \.name) { t in teacher(t) }
            }
        }
        .task { await model.didactics.ensure() }
        .quickLookPreview($preview)
        .alert("Contenuto", isPresented: Binding(get: { text != nil }, set: { if !$0 { text = nil } })) {
            Button("Chiudi", role: .cancel) {}
        } message: { Text(text ?? "") }
    }

    private func teacher(_ t: DidTeacher) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                Avatar(name: t.name, size: 40)
                VStack(alignment: .leading) {
                    Text(t.name).font(.headline)
                    Text(t.folders.count == 1 ? "1 cartella" : "\(t.folders.count) cartelle").font(.caption).foregroundStyle(.subtle)
                }
            }
            ForEach(Array(t.folders.enumerated()), id: \.offset) { i, f in
                let key = "\(t.name)-\(i)"
                let open = expanded.contains(key)
                Button {
                    withAnimation(.snappy) { if open { expanded.remove(key) } else { expanded.insert(key) } }
                } label: {
                    HStack {
                        Image(systemName: "folder.fill").foregroundStyle(.tint)
                        VStack(alignment: .leading) {
                            Text(f.name.ifBlank ?? "Senza nome").fontWeight(.medium).lineLimit(2)
                            Text("\(f.contents.count) elementi · \(shortDate(f.date))").font(.caption).foregroundStyle(.subtle)
                        }
                        Spacer()
                        Image(systemName: "chevron.down").rotationEffect(.degrees(open ? 180 : 0)).foregroundStyle(.subtle)
                    }
                    .foregroundStyle(.primary)
                    .contentShape(.rect)
                }
                .buttonStyle(.plain)
                if open {
                    ForEach(f.contents, id: \.id) { c in
                        Button { openItem(c) } label: {
                            HStack {
                                Image(systemName: c.type == "link" ? "link" : c.type == "text" ? "text.alignleft" : c.name.lowercased().hasSuffix(".pdf") ? "doc.richtext" : "doc")
                                    .frame(width: 24)
                                Text(c.name.ifBlank ?? "Contenuto").font(.subheadline).lineLimit(2).multilineTextAlignment(.leading)
                                Spacer()
                                if opening == c.id { ProgressView() }
                            }
                            .foregroundStyle(.primary)
                            .padding(.leading, 28)
                            .contentShape(.rect)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .glassCard(padding: 16)
    }

    private func openItem(_ c: DidContent) {
        opening = c.id
        Task {
            if let opened = try? await model.api.didacticItem(c) {
                switch opened {
                case .file(let url): preview = url
                case .link(let link): if let url = URL(string: link) { openURL(url) }
                case .text(let t): text = t
                }
            } else {
                text = "Impossibile aprire il file"
            }
            opening = nil
        }
    }
}

struct DocumentsView: View {
    @Environment(Model.self) private var model
    @State private var preview: URL?
    @State private var failed: String?

    var body: some View {
        Screen(refresh: { await model.refresh([model.documents]) }) {
            NavigationLink(value: Page.web("Esito scrutini", WebLinks.reports)) {
                ItemRow(headline: "Esito scrutini", overline: "Dal sito web del registro", supporting: "Primo e secondo periodo, con voti ed esiti", interactive: true,
                        leading: AnyView(IconBadge(icon: "chart.bar.doc.horizontal", color: .indigo)),
                        trailing: AnyView(Image(systemName: "chevron.right").foregroundStyle(.subtle)))
            }
            .buttonStyle(.plain)
            Loaded(model.documents) {
                let docs = model.documents.data ?? Documents(docs: [], reports: [])
                Header(text: "Pagelle")
                if docs.reports.isEmpty { EmptyCard(text: "Nessuna pagella disponibile") }
                ForEach(Array(docs.reports.enumerated()), id: \.offset) { _, r in
                    NavigationLink(value: Page.web(r.title, r.url)) {
                        ItemRow(headline: r.title, overline: "Si apre dal sito", interactive: true, leading: AnyView(IconBadge(icon: "doc.text", color: .blue)))
                    }
                    .buttonStyle(.plain)
                }
                Header(text: "Documenti")
                if docs.docs.isEmpty { EmptyCard(text: "Nessun documento") }
                ForEach(Array(docs.docs.enumerated()), id: \.offset) { _, d in
                    Button {
                        Task { do { preview = try await model.api.document(d) } catch { failed = error.userText } }
                    } label: {
                        ItemRow(headline: d.title, interactive: true, leading: AnyView(IconBadge(icon: "doc.richtext", color: .red)))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .task { await model.documents.ensure() }
        .quickLookPreview($preview)
        .alert("Documento non disponibile", isPresented: Binding(get: { failed != nil }, set: { if !$0 { failed = nil } })) {
            Button("OK", role: .cancel) {}
        } message: { Text(failed ?? "") }
    }
}

/// Pagina del registro web, già con l'accesso fatto.
struct WebScreen: View {
    let title: String
    let url: String
    @Environment(Model.self) private var model
    @State private var ready = false

    var body: some View {
        Group {
            if ready, let u = URL(string: url) { WebKitView(url: u).ignoresSafeArea(edges: .bottom) }
            else { ProgressView("Accesso al registro web…").frame(maxWidth: .infinity, maxHeight: .infinity) }
        }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                if let u = URL(string: url) { Link(destination: u) { Image(systemName: "safari") }.accessibilityLabel("Apri in Safari") }
            }
        }
        .task {
            _ = try? await model.api.webSession()
            let store = WKWebsiteDataStore.default().httpCookieStore
            for c in HTTPCookieStorage.shared.cookies ?? [] { await store.setCookie(c) }
            ready = true
        }
    }
}

private struct WebKitView: UIViewRepresentable {
    let url: URL
    func makeUIView(context: Context) -> WKWebView {
        let view = WKWebView()
        view.allowsBackForwardNavigationGestures = true
        view.load(URLRequest(url: url))
        return view
    }
    func updateUIView(_ view: WKWebView, context: Context) {}
}

import SwiftUI
import QuickLook

struct NoticesView: View {
    @Environment(Model.self) private var model
    @State private var filter = "Tutte"
    @State private var query = ""
    @State private var confirm = false
    /// Circolari segnate finora e quante sono in tutto, mentre "Segna tutte come lette" lavora.
    @State private var progress: (done: Int, total: Int)?
    @State private var result: String?

    var body: some View {
        Screen(refresh: { await model.refresh([model.notices]) }) {
            Loaded(model.notices) { content }
        }
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "Cerca nelle circolari")
        .alert("Segnare \(unread.count) circolari come lette?", isPresented: $confirm) {
            Button("Segna come lette") { readAll() }
            Button("Annulla", role: .cancel) {}
        } message: {
            Text("Su Classeviva risulteranno lette, come se le avessi aperte una per una: per la scuola può valere come presa visione. Non si può annullare.")
        }
        .alert(result ?? "", isPresented: Binding(get: { result != nil }, set: { if !$0 { result = nil } })) {
            Button("OK", role: .cancel) {}
        }
        .overlay {
            if let progress {
                VStack(spacing: 12) {
                    Text("Segno come lette…").font(.headline)
                    ProgressView(value: Double(progress.done), total: Double(max(progress.total, 1)))
                    Text("\(progress.done) di \(progress.total)").font(.subheadline).foregroundStyle(.subtle)
                }
                .frame(width: 240)
                .padding(24)
                .glassEffect(.regular, in: .rect(cornerRadius: 24))
            }
        }
    }

    private var unread: [Notice] { (model.notices.data ?? []).filter { !$0.read } }

    /// Una alla volta, come se si aprissero: Classeviva non ha un "segna tutte".
    private func readAll() {
        let todo = unread
        progress = (0, todo.count)
        Task {
            var failed = 0
            for (i, n) in todo.enumerated() {
                if model.debugName == nil {
                    do { _ = try await model.api.readNotice(n) } catch { failed += 1 }
                }
                progress = (i + 1, todo.count)
            }
            Checker.dismissNotices(account: model.api.current, ids: todo.map(\.id))
            await model.notices.refresh()
            progress = nil
            result = failed == 0 ? (todo.count == 1 ? "Circolare segnata come letta" : "\(todo.count) circolari segnate come lette")
                : "\(failed) circolari non sono state segnate: riprova"
        }
    }

    @ViewBuilder private var content: some View {
        let list = model.notices.data ?? []
        let categories = ["Tutte"] + uniqueCategories(list)
        let words = query.lowercased().split(whereSeparator: \.isWhitespace).map(String.init)
        let shown = list.filter { n in
            (filter == "Tutte" || n.category == filter) && words.allSatisfy { w in
                n.title.lowercased().contains(w) || n.category.lowercased().contains(w) || n.attachments.contains { $0.name.lowercased().contains(w) }
            }
        }
        if categories.count > 2 { ChipRow(options: categories, selected: $filter) { $0 } }
        if unread.count > 1 {
            Button { confirm = true } label: {
                Label("Segna tutte come lette (\(unread.count))", systemImage: "checkmark.circle")
                    .font(.body.weight(.semibold)).frame(maxWidth: .infinity).padding(.vertical, 6)
            }
            .buttonStyle(.glass)
            .disabled(progress != nil)
        }
        if shown.isEmpty { EmptyCard(text: words.isEmpty ? "Nessuna comunicazione" : "Nessuna circolare trovata") }
        ForEach(Array(shown.enumerated()), id: \.offset) { _, n in
            NavigationLink(value: Page.notice(n)) { NoticeRow(notice: n) }.buttonStyle(.plain)
        }
    }
}

private func uniqueCategories(_ list: [Notice]) -> [String] {
    var seen = Set<String>()
    return list.map(\.category).filter { !$0.trimmed.isEmpty && seen.insert($0).inserted }
}

private struct NoticeRow: View {
    let notice: Notice
    @Environment(\.appTint) private var tint

    var body: some View {
        let count = notice.attachments.count
        ItemRow(
            headline: notice.title,
            overline: [shortDate(notice.date), notice.category].filter { !$0.trimmed.isEmpty }.joined(separator: " · "),
            supporting: count == 0 ? nil : count == 1 ? "1 PDF allegato" : "\(count) PDF allegati",
            bold: !notice.read, interactive: true,
            leading: AnyView(Circle().fill(notice.read ? Color.gray.opacity(0.35) : tint).frame(width: 10, height: 10)),
            trailing: AnyView(Group {
                if count > 1 {
                    Label("\(count)", systemImage: "paperclip").font(.subheadline.bold()).padding(.horizontal, 10).padding(.vertical, 6).glassEffect(.regular, in: .capsule)
                } else if count == 1 {
                    Image(systemName: "doc.richtext").foregroundStyle(tint)
                } else {
                    Image(systemName: "chevron.right").foregroundStyle(.subtle)
                }
            })
        )
    }
}

/// Testo della comunicazione e allegati. Aprendola viene segnata come letta.
struct NoticeView: View {
    let notice: Notice
    @Environment(Model.self) private var model
    @State private var text: String?
    @State private var error: String?
    @State private var opening: Int?
    @State private var preview: URL?
    @State private var failed: String?

    var body: some View {
        Screen {
            VStack(alignment: .leading, spacing: 6) {
                Text(notice.title).font(.title2.weight(.bold))
                Text([longDate(notice.date), notice.category].filter { !$0.trimmed.isEmpty }.joined(separator: " · ")).foregroundStyle(.subtle)
            }
            .padding(.horizontal, 4)
            .padding(.bottom, 6)

            if let text {
                if !text.isEmpty { Text(text).textSelection(.enabled).glassCard(padding: 18) }
            } else if let error {
                MessageView(text: error) { Task { await load() } }
            } else {
                ProgressView().frame(maxWidth: .infinity).padding(.vertical, 32)
            }

            if !notice.attachments.isEmpty {
                Header(text: notice.attachments.count == 1 ? "Allegato" : "Allegati")
                ForEach(notice.attachments, id: \.num) { a in
                    Button { open(a) } label: {
                        ItemRow(headline: a.name.ifBlank ?? "Allegato \(a.num)", supporting: "PDF", interactive: true,
                                leading: AnyView(IconBadge(icon: "doc.richtext", color: .red)),
                                trailing: AnyView(Group {
                                    if opening == a.num { ProgressView() } else { Image(systemName: "arrow.down.circle").foregroundStyle(.subtle) }
                                }))
                    }
                    .buttonStyle(.plain)
                    .disabled(opening != nil)
                }
            }
        }
        .navigationTitle("Comunicazione")
        .navigationBarTitleDisplayMode(.inline)
        .quickLookPreview($preview)
        .alert("Impossibile aprire l'allegato", isPresented: Binding(get: { failed != nil }, set: { if !$0 { failed = nil } })) {
            Button("OK", role: .cancel) {}
        } message: { Text(failed ?? "") }
        .task {
            Checker.dismissNotices(account: model.api.current, ids: [notice.id])
            await load()
        }
        .onDisappear { Task { await model.notices.refresh() } }
    }

    private func load() async {
        error = nil
        guard model.debugName == nil else { text = "Testo della comunicazione di esempio."; return }
        do { text = try await model.api.readNotice(notice) } catch { self.error = error.userText }
    }

    private func open(_ a: Attachment) {
        opening = a.num
        Task {
            // Prima la circolare va segnata come letta, altrimenti Classeviva risponde 404 sull'allegato.
            if text == nil { await load() }
            do { preview = try await model.api.attachment(notice, a) } catch { failed = error.userText }
            opening = nil
        }
    }
}

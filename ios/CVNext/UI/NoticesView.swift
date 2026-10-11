import SwiftUI
import QuickLook

struct NoticesView: View {
    @Environment(Model.self) private var model
    @State private var filter = "Tutte"

    var body: some View {
        Screen(refresh: { await model.refresh([model.notices]) }) {
            Loaded(model.notices) { content }
        }
    }

    @ViewBuilder private var content: some View {
        let list = model.notices.data ?? []
        let categories = ["Tutte"] + uniqueCategories(list)
        let shown = list.filter { filter == "Tutte" || $0.category == filter }
        if categories.count > 2 { ChipRow(options: categories, selected: $filter) { $0 } }
        if shown.isEmpty { EmptyCard(text: "Nessuna comunicazione") }
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
                    Image(systemName: "chevron.right").foregroundStyle(.secondary)
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
                Text([longDate(notice.date), notice.category].filter { !$0.trimmed.isEmpty }.joined(separator: " · ")).foregroundStyle(.secondary)
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
                                    if opening == a.num { ProgressView() } else { Image(systemName: "arrow.down.circle").foregroundStyle(.secondary) }
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
        .task { await load() }
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
            do { preview = try await model.api.attachment(notice, a) } catch { failed = error.userText }
            opening = nil
        }
    }
}

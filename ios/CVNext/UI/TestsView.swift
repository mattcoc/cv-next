import SwiftUI

/// Tutte le verifiche e interrogazioni dell'anno scolastico: prima quelle in arrivo, poi quelle già fatte.
struct TestsView: View {
    @Environment(Model.self) private var model
    @State private var kind = "Tutte"

    var body: some View {
        Screen(refresh: { await model.refresh([model.yearAgenda, model.lessons]) }) {
            Loaded(model.yearAgenda) { content }
        }
        .navigationTitle("Verifiche e interrogazioni")
        .navigationBarTitleDisplayMode(.inline)
        .task { await model.yearAgenda.ensure() }
    }

    @ViewBuilder private var content: some View {
        let all = (model.yearAgenda.data ?? []).filter(\.isTest)
        let teachers = Teachers(model.lessons.data ?? [])
        let todayIso = Day.iso(Day.today)
        let both = Set(all.map(\.testKind)).count > 1
        let shown = all.filter { !both || kind == "Tutte" || $0.testKind == kind }
        let next = shown.filter { $0.date >= todayIso }.sorted { $0.date + $0.time < $1.date + $1.time }
        let past = shown.filter { $0.date < todayIso }.sorted { $0.date + $0.time > $1.date + $1.time }
        let names = model.myNames

        if both {
            ChipRow(options: ["Tutte", "Verifica", "Interrogazione"], selected: $kind) {
                $0 == "Verifica" ? "Verifiche" : $0 == "Interrogazione" ? "Interrogazioni" : $0
            }
        }
        Header(text: "In arrivo (\(next.count))")
        if next.isEmpty {
            EmptyCard(text: "Nessuna verifica in programma", icon: "calendar.badge.checkmark")
        }
        ForEach(Array(next.enumerated()), id: \.offset) { _, e in
            TestRow(event: e, teachers: teachers, mine: Mentions.matches("\(e.notes) \(e.subject)", names))
        }
        if !past.isEmpty {
            Header(text: "Già fatte (\(past.count))")
            ForEach(Array(past.enumerated()), id: \.offset) { _, e in
                TestRow(event: e, teachers: teachers, mine: Mentions.matches("\(e.notes) \(e.subject)", names))
            }
        }
    }
}

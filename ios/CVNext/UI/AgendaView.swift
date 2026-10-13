import SwiftUI

struct AgendaView: View {
    @Environment(Model.self) private var model
    @State var filter: String
    private let days: [Date] = (-30...30).map { Day.add(Day.today, $0) }
    @State private var selected = Day.today

    var body: some View {
        Screen(refresh: { await model.refresh([model.agenda, model.lessons]) }) {
            Loaded(model.agenda) { content }
        }
    }

    @ViewBuilder private var content: some View {
        let events = model.agenda.data ?? []
        let teachers = Teachers(model.lessons.data ?? [])
        let todayIso = Day.iso(Day.today)
        let homework = events.filter { $0.homework && $0.date >= todayIso }
        let past = events.filter { $0.homework && $0.date < todayIso }.sorted { $0.date > $1.date }

        let todo = homework.filter { !model.done.isDone($0) }.count
        ChipRow(options: ["Tutto", "Compiti"], selected: $filter) { $0 == "Compiti" ? "Compiti · \(todo)" : $0 }

        if filter == "Tutto" {
            let sel = Day.iso(selected)
            let dayList = events.filter { $0.date == sel }
            let next = events.filter { $0.date > sel && $0.date > todayIso }
            DayStrip(days: days, selected: $selected) { d in events.contains { $0.date == Day.iso(d) } }
            VStack(alignment: .leading, spacing: 10) {
                DayLabel(day: selected)
                if dayList.isEmpty { EmptyCard(text: "Niente in programma") }
                ForEach(Array(dayList.enumerated()), id: \.offset) { _, e in EventRow(event: e, teachers: teachers) }
            }
            .id(selected)
            .transition(.opacity)
            .daySwipe(days, $selected)
            if !next.isEmpty {
                Header(text: "Prossimi giorni")
                ForEach(Array(next.enumerated()), id: \.offset) { _, e in EventRow(event: e, teachers: teachers, showDate: true) }
            }
        } else {
            if homework.isEmpty { EmptyCard(text: "Nessun compito nei prossimi 30 giorni") }
            let dates = Array(Set(homework.map(\.date))).sorted()
            ForEach(dates, id: \.self) { date in
                Header(text: longDate(date))
                ForEach(Array(homework.filter { $0.date == date }.enumerated()), id: \.offset) { _, e in EventRow(event: e, teachers: teachers) }
            }
            if !past.isEmpty {
                Header(text: "Compiti passati")
                ForEach(Array(past.enumerated()), id: \.offset) { _, e in EventRow(event: e, teachers: teachers, showDate: true) }
            }
        }
    }
}

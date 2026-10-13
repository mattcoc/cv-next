import SwiftUI
import Charts

private let year = -1

struct GradesView: View {
    @Environment(Model.self) private var model
    @State private var period: Int?
    @State private var bySubject = true

    var body: some View {
        Screen(refresh: { await model.refresh([model.grades, model.periods]) }) {
            Loaded(model.grades) { content }
        }
    }

    private func periods(_ all: [Grade]) -> [Period] {
        if let p = model.periods.data, !p.isEmpty { return p }
        var seen = Set<Int>()
        return all.sorted { $0.periodPos < $1.periodPos }
            .filter { !$0.period.trimmed.isEmpty && seen.insert($0.periodPos).inserted }
            .map { Period(pos: $0.periodPos, name: $0.period.pretty, start: "", end: "") }
    }

    @ViewBuilder private var content: some View {
        let all = model.grades.data ?? []
        let periods = periods(all)
        let start = periods.first { $0.current }?.pos ?? periods.last { p in all.contains { $0.periodPos == p.pos } }?.pos ?? year
        let current = period ?? start
        let of = { (pos: Int) -> [Grade] in pos == year ? all : all.filter { $0.periodPos == pos } }
        let name = { (pos: Int) -> String in periods.first { $0.pos == pos }?.name ?? "Anno" }
        let grades = of(current)
        let bySubjectMap = Dictionary(grouping: grades, by: \.subject)
        let subjectNames = bySubjectMap.keys.sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
        let subjects = subjectNames.map { ($0, bySubjectMap[$0] ?? []) }
        let avg = grades.subjectsAvg

        if periods.count > 1 {
            ScrollView(.horizontal) {
                GlassEffectContainer(spacing: 8) {
                    HStack(spacing: 8) {
                        ForEach(periods.map(\.pos) + [year], id: \.self) { pos in
                            PeriodCard(name: name(pos), avg: of(pos).subjectsAvg, count: of(pos).count, selected: pos == current) {
                                withAnimation(.snappy) { period = pos }
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                }
            }
            .scrollIndicators(.hidden)
            .padding(.horizontal, -16)
        }

        AverageCard(title: current == year ? "Media dell'anno" : "Media · \(name(current))", avg: avg, grades: grades, subjects: subjects)

        Picker("Vista", selection: $bySubject) {
            Text("Materie").tag(true)
            Text("Recenti").tag(false)
        }
        .pickerStyle(.segmented)
        .padding(.vertical, 4)

        if grades.isEmpty {
            EmptyCard(text: current == year ? "Nessun voto quest'anno" : "Nessun voto nel \(name(current).lowercased())")
        }
        if bySubject {
            ForEach(subjectNames, id: \.self) { subject in
                NavigationLink(value: Page.subject(subject)) {
                    SubjectRow(subject: subject, list: bySubjectMap[subject] ?? [], new: (bySubjectMap[subject] ?? []).contains { model.freshGrades.contains($0.key) })
                }.buttonStyle(.plain)
            }
        } else {
            let fresh = model.freshGrades
            ForEach(Array(grades.enumerated()), id: \.offset) { _, g in
                ItemRow(headline: g.subject, overline: [shortDate(g.date), g.type].filter { !$0.isEmpty }.joined(separator: " · "),
                        supporting: g.notes, bold: fresh.contains(g.key), leading: AnyView(GradeCircle(grade: g)),
                        trailing: fresh.contains(g.key) ? AnyView(NewBadge()) : nil)
            }
        }
    }
}

private struct PeriodCard: View {
    let name: String
    let avg: Double?
    let count: Int
    let selected: Bool
    let action: () -> Void
    @Environment(\.appTint) private var tint

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 2) {
                Text(name).font(.subheadline.weight(.semibold)).lineLimit(1).minimumScaleFactor(0.7)
                HStack(spacing: 6) {
                    Circle().fill(avg == nil ? Color.gray.opacity(0.4) : gradeColor(avg)).frame(width: 8, height: 8)
                    Text(avg.map(num) ?? "–").font(.title2.bold())
                }
                Text(count == 1 ? "1 voto" : "\(count) voti").font(.caption).foregroundStyle(.subtle)
            }
            .foregroundStyle(.primary)
            .frame(width: 112, alignment: .leading)
            .padding(14)
        }
        .buttonStyle(.plain)
        .glassEffect(selected ? .regular.tint(tint.opacity(0.45)).interactive() : .regular.interactive(), in: .rect(cornerRadius: 22))
    }
}

private struct AverageCard: View {
    let title: String
    let avg: Double?
    let grades: [Grade]
    let subjects: [(String, [Grade])]

    var body: some View {
        let averages = subjects.compactMap { s in s.1.gradeAvg.map { (s.0, $0) } }
        let low = averages.filter { $0.1 < 6 }.map(\.0)
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.headline)
                    Text(avg.map(num) ?? "–").font(.system(size: 52, weight: .bold, design: .rounded)).contentTransition(.numericText())
                    Text("\(grades.count) voti · \(subjects.count) materie").font(.subheadline)
                }
                Spacer()
                AverageRing(avg: avg, size: 76, label: false)
            }
            if !averages.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    Text("\(averages.count - low.count) su \(averages.count) materie sufficienti").fontWeight(.medium)
                    if !low.isEmpty { Text("Da recuperare: " + low.joined(separator: ", ")).lineLimit(2) }
                }
                .font(.subheadline)
            }
        }
        .glassCard(avg == nil ? nil : gradeColor(avg), padding: 20)
        .animation(.smooth, value: avg)
    }
}

private struct SubjectRow: View {
    let subject: String
    let list: [Grade]
    var new = false

    var body: some View {
        let avg = list.gradeAvg
        HStack(spacing: 16) {
            AverageRing(avg: avg)
            VStack(alignment: .leading, spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 8) {
                        Text(subject).font(.headline).lineLimit(2).multilineTextAlignment(.leading)
                        if new { NewBadge() }
                    }
                    if avg == nil { Text("Nessun voto che fa media").font(.caption).foregroundStyle(.subtle) }
                    else if let avg, avg < 6 { Text("Da recuperare").font(.caption.weight(.semibold)).foregroundStyle(.red) }
                }
                HStack(spacing: 6) { ForEach(Array(list.prefix(5).enumerated()), id: \.offset) { _, g in GradeChip(grade: g) } }
            }
            Spacer(minLength: 0)
            Image(systemName: "chevron.right").foregroundStyle(.subtle)
        }
        .foregroundStyle(.primary)
        .glassCard(interactive: true, padding: 16)
    }
}

/// Dettaglio di una materia: media, andamento, quanto serve, voti per periodo.
struct SubjectView: View {
    let subject: String
    @Environment(Model.self) private var model

    var body: some View {
        let list = (model.grades.data ?? []).filter { $0.subject == subject }
        let periods = model.periods.data ?? []
        let groups = Dictionary(grouping: list, by: \.periodPos)
        let periodName = { (pos: Int) -> String in periods.first { $0.pos == pos }?.name ?? groups[pos]?.first?.period.pretty ?? "" }
        Screen {
            HStack(spacing: 16) {
                AverageRing(avg: list.gradeAvg, size: 64)
                VStack(alignment: .leading) {
                    Text("Media dell'anno").font(.headline)
                    Text(list.count == 1 ? "1 voto" : "\(list.count) voti").foregroundStyle(.subtle)
                }
            }
            .padding(.horizontal, 4)
            TrendCard(list: list)
            TargetCard(list: list, periods: periods)
            ForEach(groups.keys.sorted(), id: \.self) { pos in
                let g = groups[pos] ?? []
                Header(text: "\(periodName(pos)) · media \(g.gradeAvg.map(num) ?? "–")")
                ForEach(Array(g.enumerated()), id: \.offset) { _, grade in
                    ItemRow(headline: grade.type.ifBlank ?? "Voto", overline: longDate(grade.date), supporting: grade.notes, leading: AnyView(GradeCircle(grade: grade)))
                }
            }
        }
        .navigationTitle(subject)
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// "Quanto devo prendere?": il voto che serve nel periodo in corso per arrivare alla media scelta.
private struct TargetCard: View {
    let list: [Grade]
    let periods: [Period]
    @State private var target = 6

    var body: some View {
        if let pos = periods.first(where: { $0.current })?.pos ?? list.map(\.periodPos).max() {
            let values = list.filter { $0.periodPos == pos && $0.counts }.compactMap(\.decimal)
            let periodName = periods.first { $0.pos == pos }?.name ?? list.first { $0.periodPos == pos }?.period.pretty ?? ""
            let t = Double(target)
            let text: String = {
                switch neededGrade(values, t) {
                case .reached: "Hai già almeno \(target) di media\(periodName.isEmpty ? "" : " nel \(periodName)"). Per restarci, al prossimo voto basta un \(num(keepGrade(values, t)))."
                case .one(let g): "Al prossimo voto ti serve almeno \(num(g)) per arrivare a \(target)."
                case .two(let g): "Con un solo voto non basta: servono due voti da almeno \(num(g))."
                case .impossible: "Per arrivare a \(target) servono più di due voti alti."
                }
            }()
            VStack(alignment: .leading, spacing: 12) {
                Text("Quanto devo prendere?").font(.headline)
                Picker("Media", selection: $target) {
                    ForEach([6, 7, 8, 9], id: \.self) { Text("Media \($0)").tag($0) }
                }
                .pickerStyle(.segmented)
                Text(text).font(.subheadline).foregroundStyle(.subtle).contentTransition(.opacity)
            }
            .glassCard(padding: 16)
        }
    }
}

/// Andamento dei voti che fanno media: punti per i voti, linea per la media man mano, riga tratteggiata al 6.
private struct TrendCard: View {
    let list: [Grade]
    @State private var selection: Int?
    @Environment(\.appTint) private var tint

    var body: some View {
        let points = list.filter { $0.counts && $0.decimal != nil }.sorted { $0.date < $1.date }
        if points.count >= 2 {
            let values = points.map { $0.decimal ?? 0 }
            let averages = values.indices.map { i in Array(values[0...i]).avg ?? 0 }
            let low = max(floor(min(values.min() ?? 6, averages.min() ?? 6) - 1), 1)
            let picked = min(max(selection ?? points.count - 1, 0), points.count - 1)
            let g = points[picked]
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("Andamento").font(.headline)
                    Spacer()
                    Label("Voti", systemImage: "circle.fill").labelStyle(.titleAndIcon).font(.caption2).foregroundStyle(.subtle)
                    Label("Media", systemImage: "line.diagonal").font(.caption2).foregroundStyle(tint)
                }
                Text("\(g.value) · \(g.type.ifBlank ?? "Voto") · \(shortDate(g.date)) · media \(num(averages[picked]))").font(.subheadline)
                Chart {
                    RuleMark(y: .value("Sufficienza", 6))
                        .lineStyle(StrokeStyle(lineWidth: 1, dash: [5, 4]))
                        .foregroundStyle(.subtle)
                    ForEach(points.indices, id: \.self) { i in
                        LineMark(x: .value("Voto", i), y: .value("Media", averages[i]))
                            .foregroundStyle(tint)
                            .interpolationMethod(.monotone)
                    }
                    ForEach(points.indices, id: \.self) { i in
                        PointMark(x: .value("Voto", i), y: .value("Valore", values[i]))
                            .foregroundStyle(gradeColor(values[i]))
                            .symbolSize(i == picked ? 140 : 70)
                    }
                }
                .chartYScale(domain: low...10)
                .chartXAxis(.hidden)
                .chartXSelection(value: $selection)
                .frame(height: 150)
                HStack {
                    Text(shortDate(points[0].date))
                    Spacer()
                    Text("Tocca un punto per i dettagli")
                    Spacer()
                    Text(shortDate(points[points.count - 1].date))
                }
                .font(.caption2)
                .foregroundStyle(.subtle)
            }
            .glassCard(padding: 16)
        }
    }
}

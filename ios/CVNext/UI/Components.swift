import SwiftUI

extension Color {
    init(hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }
}

/// Gli stessi colori tra cui si sceglie su Android.
enum Palette {
    static let options: [(String, Color)] = [
        ("Indaco", Color(hex: 0x3F51B5)), ("Blu", Color(hex: 0x1E88E5)), ("Turchese", Color(hex: 0x00897B)),
        ("Verde", Color(hex: 0x43A047)), ("Giallo", Color(hex: 0xF9A825)), ("Arancio", Color(hex: 0xF4511E)),
        ("Rosso", Color(hex: 0xE53935)), ("Rosa", Color(hex: 0xD81B60)), ("Viola", Color(hex: 0x8E24AA))
    ]
    static func color(_ i: Int) -> Color { options.indices.contains(i) ? options[i].1 : options[0].1 }
}

extension ShapeStyle where Self == Color {
    /// Testo secondario un po' più marcato di `.secondary`, leggibile anche sopra il vetro.
    static var subtle: Color { Color.primary.opacity(0.72) }
}

extension EnvironmentValues {
    @Entry var appTint: Color = Palette.color(0)
}

/// Sfondo con macchie di colore sfocate: dà al vetro qualcosa da rifrangere.
struct AppBackground: View {
    @Environment(\.appTint) private var tint
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        ZStack {
            Color(.systemGroupedBackground)
            Circle().fill(tint.opacity(scheme == .dark ? 0.45 : 0.30)).frame(width: 420, height: 420).blur(radius: 110).offset(x: -150, y: -320)
            Circle().fill(Color.teal.opacity(scheme == .dark ? 0.25 : 0.18)).frame(width: 360, height: 360).blur(radius: 120).offset(x: 170, y: 40)
            Circle().fill(tint.opacity(scheme == .dark ? 0.30 : 0.18)).frame(width: 380, height: 380).blur(radius: 120).offset(x: -120, y: 420)
        }
        .ignoresSafeArea()
    }
}

extension View {
    /// Scheda di vetro: il mattone di ogni schermata.
    func glassCard(_ tint: Color? = nil, interactive: Bool = false, radius: CGFloat = 24, padding: CGFloat = 14) -> some View {
        var glass = Glass.regular
        if let tint { glass = glass.tint(tint.opacity(0.35)) }
        if interactive { glass = glass.interactive() }
        return self.padding(padding).frame(maxWidth: .infinity, alignment: .leading).glassEffect(glass, in: .rect(cornerRadius: radius))
    }
}

/// Elenco a scorrimento con lo sfondo dell'app e il trascina-per-aggiornare.
struct Screen<Content: View>: View {
    var refresh: (() async -> Void)? = nil
    @ViewBuilder var content: () -> Content

    var body: some View {
        ScrollView {
            // Su iPad il contenuto resta in una colonna leggibile al centro
            VStack(alignment: .leading, spacing: 10) { content() }
                .padding(.horizontal, 16)
                .padding(.top, 4)
                .padding(.bottom, 32)
                .frame(maxWidth: 760)
                .frame(maxWidth: .infinity)
        }
        .scrollIndicators(.hidden)
        .background { AppBackground() }
        .refreshable { await refresh?() }
    }
}

/// Mostra il contenuto quando tutti i dati sono pronti, altrimenti caricamento o errore.
struct Loaded<Content: View>: View {
    let res: [any Loadable]
    @ViewBuilder var content: () -> Content

    init(_ res: any Loadable..., @ViewBuilder content: @escaping () -> Content) {
        self.res = res
        self.content = content
    }

    var body: some View {
        let ready = res.allSatisfy { $0.ready }
        let error = res.compactMap { $0.error }.first
        if ready {
            if let error { OfflineBanner(text: error) { Task { for r in res { await r.refresh() } } } }
            content()
        } else if let error {
            MessageView(text: error) { Task { for r in res { await r.refresh() } } }
        } else {
            VStack(spacing: 16) {
                ProgressView().controlSize(.large)
                Text("Caricamento…").foregroundStyle(.subtle)
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 96)
        }
    }
}

struct OfflineBanner: View {
    let text: String
    let retry: () -> Void
    var body: some View {
        Button(action: retry) {
            HStack(spacing: 10) {
                Image(systemName: "icloud.slash")
                VStack(alignment: .leading, spacing: 2) {
                    Text("Dati dell'ultimo aggiornamento").font(.subheadline.weight(.semibold))
                    Text(text).font(.caption).foregroundStyle(.subtle)
                }
                Spacer()
                Text("Riprova").font(.subheadline.weight(.semibold)).foregroundStyle(.tint)
            }
            .glassCard(interactive: true, radius: 20, padding: 12)
        }
        .buttonStyle(.plain)
    }
}

struct MessageView: View {
    let text: String
    var retry: (() -> Void)? = nil
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "icloud.slash").font(.title2).frame(width: 56, height: 56).glassEffect(.regular.tint(.red.opacity(0.3)), in: .circle)
            Text(text).multilineTextAlignment(.center).foregroundStyle(.subtle)
            if let retry {
                Button(action: retry) { Label("Riprova", systemImage: "arrow.clockwise") }.buttonStyle(.glass)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 64)
        .padding(.horizontal, 16)
    }
}

struct Header: View {
    let text: String
    var action: String? = nil
    var onAction: () -> Void = {}
    var body: some View {
        HStack {
            Text(text).font(.title3.weight(.semibold))
            Spacer()
            if let action { Button(action, action: onAction).font(.subheadline.weight(.semibold)) }
        }
        .padding(.top, 12)
        .padding(.horizontal, 4)
    }
}

struct EmptyCard: View {
    let text: String
    var icon: String? = nil
    var body: some View {
        VStack(spacing: 8) {
            if let icon { Image(systemName: icon).font(.title2).foregroundStyle(.subtle) }
            Text(text).multilineTextAlignment(.center).foregroundStyle(.subtle)
        }
        .frame(maxWidth: .infinity)
        .glassCard(padding: 22)
    }
}

struct SmallLabel: View {
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View { Text(text).font(.subheadline.weight(.semibold)).foregroundStyle(.subtle).padding(.leading, 4).padding(.top, 4) }
}

/// Testo lungo: tre righe, tutto con un tocco.
struct ExpandableText: View {
    let text: String
    @State private var expanded = false
    var body: some View {
        Text(text).font(.subheadline).foregroundStyle(.subtle)
            .lineLimit(expanded ? nil : 3)
            .onTapGesture { withAnimation(.snappy) { expanded.toggle() } }
    }
}

/// Riga di elenco su vetro: icona, titolo, dettagli.
struct ItemRow: View {
    var headline: String
    var overline: String? = nil
    var supporting: String? = nil
    var bold = false
    var footer: String? = nil
    var interactive = false
    var leading: AnyView? = nil
    var trailing: AnyView? = nil

    var body: some View {
        HStack(spacing: 14) {
            if let leading { leading }
            VStack(alignment: .leading, spacing: 3) {
                if let o = overline?.ifBlank { Text(o).font(.caption).foregroundStyle(.subtle) }
                Text(headline).font(.body.weight(bold ? .bold : .medium)).foregroundStyle(.primary)
                if let s = supporting?.ifBlank { ExpandableText(text: s) }
                if let f = footer?.ifBlank { Text(f).font(.caption).foregroundStyle(.subtle).padding(.top, 2) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if let trailing { trailing }
        }
        .glassCard(interactive: interactive)
    }
}

struct Badge: View {
    let text: String
    let color: Color
    var size: CGFloat = 44
    var body: some View {
        Text(text).font(.subheadline.bold()).foregroundStyle(.white)
            .minimumScaleFactor(0.6).lineLimit(1)
            .frame(width: size, height: size)
            .background(color.gradient, in: .circle)
    }
}

struct IconBadge: View {
    let icon: String
    let color: Color
    var body: some View {
        Image(systemName: icon).font(.body.weight(.semibold)).foregroundStyle(color)
            .frame(width: 44, height: 44)
            .background(color.opacity(0.18), in: .circle)
    }
}

struct Avatar: View {
    let name: String
    var size: CGFloat = 32
    @Environment(\.appTint) private var tint
    var body: some View {
        let initials = name.split(separator: " ").prefix(2).compactMap(\.first).map { String($0).uppercased() }.joined()
        Text(initials.isEmpty ? "?" : initials).font(.system(size: size * 0.4, weight: .semibold)).foregroundStyle(.white)
            .frame(width: size, height: size)
            .background(tint.gradient, in: .circle)
    }
}

// MARK: voti

func gradeColor(_ value: Double?, counts: Bool = true) -> Color {
    guard counts else { return .blue }
    guard let value else { return .gray }
    return value >= 6 ? .green : .red
}

struct GradeCircle: View {
    let grade: Grade
    var size: CGFloat = 44
    var body: some View { Badge(text: grade.value, color: gradeColor(grade.decimal, counts: grade.counts), size: size) }
}

/// Icona del tipo di prova: scritto, orale o pratico.
func gradeTypeIcon(_ type: String) -> String? {
    let t = type.lowercased()
    if t.contains("oral") { return "person.wave.2" }
    if t.contains("scritt") || t.contains("grafic") { return "pencil.line" }
    if t.contains("pratic") || t.contains("laborator") { return "wrench.and.screwdriver" }
    return nil
}

struct GradeChip: View {
    let grade: Grade
    var body: some View {
        HStack(spacing: 4) {
            Text(grade.value).font(.subheadline.bold())
            if let icon = gradeTypeIcon(grade.type) { Image(systemName: icon).font(.caption2) }
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 9).padding(.vertical, 4)
        .background(gradeColor(grade.decimal, counts: grade.counts).gradient, in: .capsule)
    }
}

struct AverageRing: View {
    let avg: Double?
    var size: CGFloat = 56
    var label = true
    var body: some View {
        let color = gradeColor(avg)
        ZStack {
            Circle().stroke(color.opacity(0.2), lineWidth: 5)
            Circle().trim(from: 0, to: (avg ?? 0) / 10)
                .stroke(color, style: StrokeStyle(lineWidth: 5, lineCap: .round))
                .rotationEffect(.degrees(-90))
            if label { Text(avg.map(num) ?? "–").font(.caption.bold()).minimumScaleFactor(0.7) }
        }
        .frame(width: size, height: size)
        .animation(.smooth, value: avg)
    }
}

/// Lettera e colore di ogni tipo di assenza, come nel registro.
func absenceStyle(_ type: String) -> (String, Color) {
    switch type {
    case "Assenza": ("A", .red)
    case "Assenza parziale": ("P", .blue)
    case "Uscita anticipata": ("U", .yellow)
    default: ("R", .orange)
    }
}

// MARK: agenda

struct EventIcon: View {
    let event: Event
    @Environment(\.appTint) private var tint
    var body: some View {
        if event.isTest { IconBadge(icon: event.testKind == "Interrogazione" ? "person.wave.2.fill" : "checklist", color: tint) }
        else if event.homework { IconBadge(icon: "pencil.and.list.clipboard", color: .orange) }
        else { IconBadge(icon: "calendar", color: .teal) }
    }
}

/// Titolo la materia, sotto il docente; se la materia non si sa, il titolo è il docente.
struct EventRow: View {
    let event: Event
    let teachers: Teachers
    var showDate = false
    var mine = false
    var body: some View {
        let subject = teachers.subject(event)
        ItemRow(
            headline: subject.ifBlank ?? event.author.ifBlank ?? event.kind,
            overline: [event.kind, showDate ? shortDate(event.date) : nil,
                       // L'orario dei compiti è solo quello di inserimento: non serve.
                       event.homework ? nil : (event.time.ifBlank ?? "Tutto il giorno"),
                       mine ? "Ci sei anche tu" : nil].compactMap { $0 }.joined(separator: " · "),
            supporting: event.notes.tidy, bold: mine,
            footer: subject.trimmed.isEmpty ? nil : event.author,
            leading: AnyView(EventIcon(event: event))
        )
    }
}

struct TestRow: View {
    let event: Event
    let teachers: Teachers
    var mine = false
    var body: some View {
        let days = Day.between(Day.today, Day.parse(event.date) ?? Day.today)
        let subject = teachers.subject(event)
        ItemRow(
            headline: subject.ifBlank ?? event.author.ifBlank ?? event.testKind,
            overline: [event.testKind, shortDate(event.date), mine ? "Ci sei anche tu" : nil].compactMap { $0 }.joined(separator: " · "),
            supporting: event.notes.tidy, bold: mine,
            footer: subject.trimmed.isEmpty ? nil : event.author,
            leading: AnyView(EventIcon(event: event)),
            trailing: AnyView(
                Text(days == 0 ? "Oggi" : days == 1 ? "Domani" : "Tra \(days) giorni")
                    .font(.caption.bold())
                    .padding(.horizontal, 10).padding(.vertical, 5)
                    .glassEffect(.regular.tint((days <= 1 ? Color.red : Color.secondary).opacity(0.3)), in: .capsule)
            )
        )
    }
}

struct LessonRow: View {
    let lesson: Lesson
    @Environment(\.appTint) private var tint
    var body: some View {
        let hours = lesson.hours > 1 ? "\(lesson.hour)-\(lesson.hour + lesson.hours - 1)" : "\(lesson.hour)ª"
        let length = lesson.hours == 1 ? "1 ora" : "\(lesson.hours) ore"
        ItemRow(
            headline: lesson.subject,
            overline: [lesson.author, length].filter { !$0.trimmed.isEmpty }.joined(separator: " · "),
            supporting: lesson.topic,
            leading: AnyView(Badge(text: hours, color: tint))
        )
    }
}

/// Striscia dei giorni: il giorno scelto è in vetro colorato.
struct DayStrip: View {
    let days: [Date]
    @Binding var selected: Date
    let marked: (Date) -> Bool
    @Environment(\.appTint) private var tint

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal) {
                GlassEffectContainer(spacing: 8) {
                    HStack(spacing: 8) {
                        ForEach(days, id: \.self) { d in
                            let sel = d == selected
                            Button { withAnimation(.snappy) { selected = d } } label: {
                                VStack(spacing: 3) {
                                    Text(Day.dayName(d)).font(.caption).opacity(0.75)
                                    Text("\(Day.number(d))").font(.title3.bold())
                                    Circle().fill(marked(d) ? (sel ? Color.white : tint) : .clear).frame(width: 5, height: 5)
                                }
                                .foregroundStyle(sel ? Color.white : Color.primary)
                                .frame(width: 50)
                                .padding(.vertical, 10)
                                .contentShape(.rect)
                            }
                            .buttonStyle(.plain)
                            .glassEffect(sel ? .regular.tint(tint).interactive() : .regular.interactive(), in: .rect(cornerRadius: 18))
                            .id(d)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 6)
                }
            }
            .scrollIndicators(.hidden)
            .padding(.horizontal, -16)
            .onAppear { proxy.scrollTo(selected, anchor: .center) }
            .onChange(of: selected) { _, s in withAnimation { proxy.scrollTo(s, anchor: .center) } }
        }
    }
}

struct DayLabel: View {
    let day: Date
    var body: some View {
        HStack {
            Text(longDate(Day.iso(day))).font(.headline)
            Spacer()
            Label("scorri", systemImage: "hand.draw").font(.caption).foregroundStyle(.subtle)
        }
        .padding(.horizontal, 4)
    }
}

extension View {
    /// Scorrendo in orizzontale si passa al giorno prima o dopo.
    func daySwipe(_ days: [Date], _ selected: Binding<Date>) -> some View {
        simultaneousGesture(DragGesture(minimumDistance: 30).onEnded { v in
            guard abs(v.translation.width) > abs(v.translation.height) * 1.5, abs(v.translation.width) > 60,
                  let i = days.firstIndex(of: selected.wrappedValue) else { return }
            let next = v.translation.width < 0 ? i + 1 : i - 1
            if days.indices.contains(next) { withAnimation(.snappy) { selected.wrappedValue = days[next] } }
        })
    }
}

/// Fila di scelte a capsula di vetro.
struct ChipRow<T: Hashable>: View {
    let options: [T]
    @Binding var selected: T
    let label: (T) -> String
    @Environment(\.appTint) private var tint

    var body: some View {
        ScrollView(.horizontal) {
            GlassEffectContainer(spacing: 8) {
                HStack(spacing: 8) {
                    ForEach(options, id: \.self) { o in
                        let sel = o == selected
                        Button { withAnimation(.snappy) { selected = o } } label: {
                            Text(label(o)).font(.subheadline.weight(.medium))
                                .foregroundStyle(sel ? Color.white : Color.primary)
                                .padding(.horizontal, 14).padding(.vertical, 8)
                        }
                        .buttonStyle(.plain)
                        .glassEffect(sel ? .regular.tint(tint).interactive() : .regular.interactive(), in: .capsule)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 4)
            }
        }
        .scrollIndicators(.hidden)
        .padding(.horizontal, -16)
    }
}

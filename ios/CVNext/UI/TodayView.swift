import SwiftUI

struct TodayView: View {
    @Environment(Model.self) private var model
    @Environment(\.appTint) private var tint

    private let today = Day.today
    @State private var showTests = false

    var body: some View {
        Screen(refresh: { await model.loadAll() }) {
            Loaded(model.lessons, model.grades, model.agenda, model.absences) { content }
        }
        .navigationDestination(isPresented: $showTests) { TestsView() }
    }

    @ViewBuilder private var content: some View {
        let grades = model.grades.data ?? []
        let agenda = model.agenda.data ?? []
        let lessons = model.lessons.data ?? []
        let absences = model.absences.data ?? []
        let payments = model.payments.data ?? []
        let teachers = Teachers(lessons)
        let pending = absences.filter { !$0.justified }.count
        let todayIso = Day.iso(today)
        let limit = Day.iso(Day.add(today, 14))
        let tests = agenda.filter { $0.isTest && $0.date >= todayIso && $0.date <= limit }
        VStack(alignment: .leading, spacing: 4) {
            Text("Ciao \(model.name.firstWord.pretty)").font(.largeTitle.weight(.bold))
            Text(longDate(todayIso)).foregroundStyle(.subtle)
            let cls = className(lessons.last { !$0.cls.isEmpty }?.cls ?? agenda.first { !$0.cls.isEmpty }?.cls ?? "")
            let info = [cls, model.school.data?.name ?? ""].filter { !$0.trimmed.isEmpty }.joined(separator: " · ")
            if !info.isEmpty {
                Label(info, systemImage: "graduationcap.fill").font(.subheadline.weight(.semibold)).foregroundStyle(tint).padding(.top, 6)
            }
        }
        .padding(.horizontal, 4)
        .padding(.bottom, 6)

        Group {
        if let update = model.update {
            UpdateBanner(release: update) { withAnimation { model.update = nil } }
        }
        if pending > 0 {
            NavigationLink(value: Page.absences) {
                HStack(spacing: 12) {
                    Image(systemName: "exclamationmark.triangle.fill")
                    Text(pending == 1 ? "1 evento da giustificare" : "\(pending) eventi da giustificare").fontWeight(.semibold)
                    Spacer()
                    Image(systemName: "chevron.right").foregroundStyle(.subtle)
                }
                .foregroundStyle(.primary)
                .glassCard(.red, interactive: true)
            }
            .buttonStyle(.plain)
        }

        if let first = payments.first {
            NavigationLink(value: Page.web("PagOnline", WebLinks.payments)) {
                HStack(spacing: 12) {
                    Image(systemName: "creditcard.fill")
                    VStack(alignment: .leading, spacing: 2) {
                        Text(payments.count == 1 ? "1 pagamento da fare" : "\(payments.count) pagamenti da fare").fontWeight(.semibold)
                        Text("\(first.title) · € \(first.amount) entro il \(shortDate(first.due))").font(.caption).lineLimit(2)
                    }
                    Spacer()
                    Image(systemName: "chevron.right").foregroundStyle(.subtle)
                }
                .foregroundStyle(.primary)
                .glassCard(.orange, interactive: true)
            }
            .buttonStyle(.plain)
        }

        if !tests.isEmpty {
            Header(text: "Verifiche e interrogazioni", action: tests.count > 3 ? "Tutte (\(tests.count))" : "Tutte") { showTests = true }
            ForEach(Array(tests.prefix(3).enumerated()), id: \.offset) { _, e in
                TestRow(event: e, teachers: teachers, mine: Mentions.matches("\(e.notes) \(e.subject)", model.myNames))
            }
        }

        }

        Group {
        Header(text: "Ultimi voti", action: "Tutti") { model.tab = .grades }
        if grades.isEmpty {
            EmptyCard(text: "Ancora nessun voto quest'anno", icon: "star")
        } else {
            EdgeRow {
                ForEach(Array(grades.prefix(10).enumerated()), id: \.offset) { _, g in
                    Button { model.tab = .grades } label: {
                        VStack(alignment: .leading, spacing: 8) {
                            GradeCircle(grade: g)
                            Text(g.subject).font(.subheadline.weight(.medium)).lineLimit(2, reservesSpace: true).multilineTextAlignment(.leading)
                            Text(shortDate(g.date)).font(.caption).foregroundStyle(.subtle)
                        }
                        .foregroundStyle(.primary)
                        .frame(width: 104, alignment: .leading)
                        .glassCard(interactive: true)
                    }
                    .buttonStyle(.plain)
                    .frame(width: 132)
                }
            }
        }

        NavigationLink(value: Page.agenda("Tutto")) {
            ItemRow(headline: "Agenda completa", supporting: "Compiti ed eventi dei prossimi 30 giorni e compiti passati", interactive: true,
                    leading: AnyView(IconBadge(icon: "calendar", color: .teal)),
                    trailing: AnyView(Image(systemName: "chevron.right").foregroundStyle(.subtle)))
        }
        .buttonStyle(.plain)
        .padding(.top, 10)
        }
    }
}

/// La giornata: striscia dei giorni e tutto quello che riguarda il giorno scelto.
struct DayView: View {
    @Environment(Model.self) private var model

    private let days: [Date] = (-14...14).map { Day.add(Day.today, $0) }.filter { !Day.isSunday($0) }
    @State private var selected: Date = (-14...14).map { Day.add(Day.today, $0) }.first { !Day.isSunday($0) && $0 >= Day.today } ?? Day.today
    @State private var addingFor: Date?
    @State private var reminderText = ""
    @State private var forward = true
    @State private var stripHeight: CGFloat = 90

    var body: some View {
        Screen(refresh: { await model.loadAll() }) {
            Loaded(model.lessons, model.grades, model.agenda, model.absences) { content }
        }
        .alert("Promemoria", isPresented: Binding(get: { addingFor != nil }, set: { if !$0 { addingFor = nil } })) {
            TextField("Es. portare il libro di disegno", text: $reminderText)
            Button("Salva") {
                if let day = addingFor { model.reminders.add(model.debugName != nil ? "debug" : (model.api.current ?? ""), Day.iso(day), reminderText) }
                reminderText = ""
            }
            Button("Annulla", role: .cancel) { reminderText = "" }
        } message: {
            Text("\(addingFor.map { longDate(Day.iso($0)) } ?? "")\nResta solo su questo telefono.")
        }
    }

    @ViewBuilder private var content: some View {
        let lessons = model.lessons.data ?? []
        let grades = model.grades.data ?? []
        let agenda = model.agenda.data ?? []
        let absences = model.absences.data ?? []
        let teachers = Teachers(lessons)
        // Cambiando giorno si ricorda la direzione, così il nuovo giorno entra dal lato giusto.
        let pick = Binding(get: { selected }, set: { d in
            forward = d >= selected
            selected = d
        })
        DayStrip(days: days, selected: pick) { d in
            let iso = Day.iso(d)
            return lessons.contains { $0.date == iso } || agenda.contains { $0.date == iso } || grades.contains { $0.date.hasPrefix(iso) }
                || absences.contains { $0.date.hasPrefix(iso) } || model.myReminders.contains { $0.date == iso }
        }
        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { stripHeight = $0 }
        // I due giorni si sovrappongono durante la dissolvenza invece di spingersi in colonna.
        ZStack(alignment: .top) {
            DayContent(day: selected, lessons: lessons, agenda: agenda, grades: grades, absences: absences, teachers: teachers,
                       reminders: model.myReminders.filter { $0.date == Day.iso(selected) },
                       onAdd: { addingFor = selected }, onRemove: { model.reminders.remove($0) })
                .id(selected)
                .transition(.asymmetric(insertion: .opacity.combined(with: .offset(x: forward ? 28 : -28)), removal: .opacity))
        }
        // Lo scorrimento vale su tutta la pagina sotto la striscia, anche nello spazio vuoto in fondo e ai lati.
        .modifier(PageSwipe(days: days, selected: pick, used: stripHeight + 50))
    }
}

/// Allunga la vista fino in fondo alla parte visibile della schermata (tolti [used] punti già occupati)
/// e fino ai bordi laterali, e su tutta quest'area lo scorrimento orizzontale cambia giorno.
private struct PageSwipe: ViewModifier {
    let days: [Date]
    let selected: Binding<Date>
    let used: CGFloat
    @Environment(\.screenSize) private var screen

    func body(content: Content) -> some View {
        // Distanza tra la colonna del contenuto e il bordo dello schermo (su iPad la colonna è al centro).
        let side = screen.width > 0 ? max((screen.width - min(screen.width, 760)) / 2, 0) + 16 : 16
        content
            .frame(maxWidth: .infinity, minHeight: max(screen.height - used, 0), alignment: .top)
            .padding(.horizontal, side)
            .contentShape(.rect)
            .daySwipe(days, selected)
            .padding(.horizontal, -side)
    }
}

/// Tutto quello che riguarda un giorno: lezioni, compiti ed eventi, promemoria, voti, presenze.
struct DayContent: View {
    let day: Date
    let lessons: [Lesson]
    let agenda: [Event]
    let grades: [Grade]
    let absences: [Absence]
    let teachers: Teachers
    var reminders: [Reminder] = []
    var onAdd: (() -> Void)? = nil
    var onRemove: (Reminder) -> Void = { _ in }

    var body: some View {
        let d = Day.iso(day)
        let dayAbsences = absences.filter { $0.date.hasPrefix(d) }
        let dayGrades = grades.filter { $0.date.hasPrefix(d) }
        let dayEvents = agenda.filter { $0.date == d }
        let dayLessons = lessons.filter { $0.date == d }
        VStack(alignment: .leading, spacing: 10) {
            DayLabel(day: day)
            if dayAbsences.isEmpty && dayGrades.isEmpty && dayEvents.isEmpty && dayLessons.isEmpty && reminders.isEmpty {
                EmptyCard(text: day > Day.today ? "Niente in programma" : "Nessuna lezione registrata",
                          icon: day > Day.today ? "calendar.badge.checkmark" : "calendar.badge.minus")
            }
            if !dayLessons.isEmpty {
                SmallLabel("Lezioni")
                ForEach(Array(dayLessons.enumerated()), id: \.offset) { _, l in LessonRow(lesson: l) }
            }
            if !dayEvents.isEmpty {
                SmallLabel(dayEvents.allSatisfy(\.homework) ? "Compiti" : "Compiti ed eventi")
                ForEach(Array(dayEvents.enumerated()), id: \.offset) { _, e in EventRow(event: e, teachers: teachers) }
            }
            if !reminders.isEmpty {
                SmallLabel("I tuoi promemoria")
                ForEach(reminders) { r in
                    ItemRow(headline: r.text, leading: AnyView(IconBadge(icon: "note.text", color: .purple)),
                            trailing: AnyView(Button { onRemove(r) } label: { Image(systemName: "xmark") }
                                .buttonStyle(.glass).buttonBorderShape(.circle).accessibilityLabel("Elimina promemoria")))
                }
            }
            if !dayGrades.isEmpty {
                SmallLabel("Voti")
                ForEach(Array(dayGrades.enumerated()), id: \.offset) { _, g in
                    ItemRow(headline: g.subject, overline: g.type, supporting: g.notes, leading: AnyView(GradeCircle(grade: g)))
                }
            }
            if !dayAbsences.isEmpty {
                SmallLabel("Presenze")
                ForEach(Array(dayAbsences.enumerated()), id: \.offset) { _, a in
                    let style = absenceStyle(a.type)
                    ItemRow(headline: a.type, supporting: a.justified ? "Giustificata" : "Da giustificare", leading: AnyView(Badge(text: style.0, color: style.1)))
                }
            }
            if let onAdd {
                Button(action: onAdd) { Label("Aggiungi promemoria", systemImage: "plus") }
                    .buttonStyle(.glass)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 2)
            }
        }
    }
}

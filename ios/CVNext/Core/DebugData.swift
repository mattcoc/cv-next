import Foundation

/// Dati di esempio usati solo dagli screenshot automatici (argomento di avvio `-debugScreen`).
enum DebugData {
    private static let today = Day.isSunday(Day.today) ? Day.add(Day.today, 1) : Day.today
    private static func day(_ offset: Int) -> String { Day.iso(Day.add(today, offset)) }
    private static var year: Int { Day.cal.component(.year, from: today) }

    static var periods: [Period] {
        [Period(pos: 1, name: "Trimestre", start: "\(year)-09-01", end: "\(year)-12-31"),
         Period(pos: 3, name: "Pentamestre", start: "\(year + 1)-01-01", end: "\(year + 1)-06-30")]
    }

    static var grades: [Grade] {
        [Grade(subject: "Lingua Inglese", date: day(0), value: "7½", decimal: 7.5, type: "Orale", notes: "Buona esposizione", period: "Trimestre", periodPos: 1, counts: true, id: 1),
         Grade(subject: "Matematica", date: day(-2), value: "8", decimal: 8, type: "Scritto", notes: "", period: "Trimestre", periodPos: 1, counts: true, id: 2),
         Grade(subject: "Matematica", date: day(-9), value: "6½", decimal: 6.5, type: "Orale", notes: "", period: "Trimestre", periodPos: 1, counts: true, id: 3),
         Grade(subject: "Storia", date: day(-5), value: "5", decimal: 5, type: "Orale", notes: "", period: "Trimestre", periodPos: 1, counts: true, id: 4),
         Grade(subject: "Matematica", date: day(-16), value: "5½", decimal: 5.5, type: "Scritto", notes: "", period: "Trimestre", periodPos: 1, counts: true, id: 6),
         Grade(subject: "Matematica", date: day(-23), value: "6", decimal: 6, type: "Orale", notes: "", period: "Trimestre", periodPos: 1, counts: true, id: 7),
         Grade(subject: "Matematica", date: day(-30), value: "4½", decimal: 4.5, type: "Scritto", notes: "", period: "Trimestre", periodPos: 1, counts: true, id: 8),
         Grade(subject: "Lingua e Letteratura Italiana", date: day(-6), value: "+", decimal: nil, type: "Orale", notes: "", period: "Trimestre", periodPos: 1, counts: false, id: 5)]
    }

    static var lessons: [Lesson] {
        [Lesson(date: day(0), hour: 1, hours: 1, subject: "Lingua e Letteratura Italiana", topic: "Tipologia C. Esempio di costruzione del tema", author: "Panzeri Maddalena", cls: "4AET"),
         Lesson(date: day(0), hour: 2, hours: 2, subject: "Elettrotecnica ed Elettronica", topic: "Induttore, induttanza e legge di Hopkinson", author: "Ricci Claudio"),
         Lesson(date: day(0), hour: 4, hours: 1, subject: "Lingua Inglese", topic: "Present perfect: esercizi", author: "Garato Giulia"),
         Lesson(date: day(-1), hour: 1, hours: 2, subject: "Sistemi Automatici", topic: "Diagrammi di flusso", author: "De Rito Mauro")]
    }

    /// Come nel registro vero: la materia degli eventi spesso è vuota e c'è solo il docente.
    static var agenda: [Event] {
        [Event(date: day(0), time: "12:00", subject: "", author: "Ricci Claudio", notes: "Svolgere il test di verifica caricato su Classroom", homework: true, id: 1),
         Event(date: day(0), time: "08:00", subject: "", author: "Garato Giulia", notes: "Pag. 127 es. 3", homework: true, id: 2),
         Event(date: day(2), time: "", subject: "", author: "De Rito Mauro", notes: "Verifica di sistemi  : circuiti di condizionamento e algoritmi", homework: false, id: 3),
         Event(date: day(3), time: "", subject: "", author: "Ricci Claudio", notes: "INTERROGAZIONE ELETTROTECNICA: Rossi, Bianchi, Verdi", homework: false, id: 4)]
    }

    /// L'anno intero: le verifiche dell'agenda più due già fatte e una più lontana.
    static var yearAgenda: [Event] {
        agenda + [
            Event(date: day(-6), time: "", subject: "", author: "Panzeri Maddalena", notes: "Verifica scritta: analisi del testo poetico", homework: false, id: 5),
            Event(date: day(-15), time: "", subject: "", author: "Garato Giulia", notes: "Interrogazione di inglese: Rossi, Neri, Conti", homework: false, id: 6),
            Event(date: day(12), time: "", subject: "", author: "Ricci Claudio", notes: "Verifica di elettrotecnica su campo magnetico e induzione", homework: false, id: 7)
        ]
    }

    static var reminders: [Reminder] { [Reminder(id: "1", account: "debug", date: day(0), text: "Portare il libro di disegno")] }

    static var absences: [Absence] {
        [Absence(date: day(-3), type: "Ritardo", justified: false, reason: ""),
         Absence(date: day(-12), type: "Assenza", justified: true, reason: "Motivi di salute"),
         Absence(date: day(-20), type: "Assenza", justified: true, reason: "Motivi di famiglia"),
         Absence(date: day(-25), type: "Assenza parziale", justified: true, reason: ""),
         Absence(date: day(-30), type: "Uscita anticipata", justified: true, reason: "Visita medica")]
    }

    static var notices: [Notice] {
        [Notice(title: "Uscita didattica al museo della scienza", date: day(-1), read: false, category: "Circolare", code: "CF", id: 1, attachments: [Attachment(name: "uscita.pdf", num: 1)]),
         Notice(title: "Calendario dei colloqui generali", date: day(-4), read: true, category: "Circolare", code: "CF", id: 2, attachments: [Attachment(name: "a.pdf", num: 1), Attachment(name: "b.pdf", num: 2)]),
         Notice(title: "Assemblea di istituto", date: day(-8), read: true, category: "Comunicazione", code: "CF", id: 3, attachments: [])]
    }
}

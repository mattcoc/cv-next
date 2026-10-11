package io.github.mattcoc.cvnext

import java.time.LocalDate

/** Dati di esempio usati solo dagli screenshot automatici della versione di sviluppo. */
object DebugData {
    private val today = LocalDate.now().let { if (it.dayOfWeek == java.time.DayOfWeek.SUNDAY) it.plusDays(1) else it }
    private fun day(offset: Long) = today.plusDays(offset).toString()

    val periods = listOf(
        Period(1, "Trimestre", "${today.year}-09-01", "${today.year}-12-31"),
        Period(3, "Pentamestre", "${today.year + 1}-01-01", "${today.year + 1}-06-30")
    )
    val grades = listOf(
        Grade("Lingua Inglese", day(0), "7½", 7.5, "Orale", "Buona esposizione", "Trimestre", 1, true, 1),
        Grade("Matematica", day(-2), "8", 8.0, "Scritto", "", "Trimestre", 1, true, 2),
        Grade("Matematica", day(-9), "6½", 6.5, "Orale", "", "Trimestre", 1, true, 3),
        Grade("Storia", day(-5), "5", 5.0, "Orale", "", "Trimestre", 1, true, 4),
        Grade("Matematica", day(-16), "5½", 5.5, "Scritto", "", "Trimestre", 1, true, 6),
        Grade("Matematica", day(-23), "6", 6.0, "Orale", "", "Trimestre", 1, true, 7),
        Grade("Matematica", day(-30), "4½", 4.5, "Scritto", "", "Trimestre", 1, true, 8),
        Grade("Lingua e Letteratura Italiana", day(-6), "+", null, "Orale", "", "Trimestre", 1, false, 5)
    )
    val lessons = listOf(
        Lesson(day(0), 1, 1, "Lingua e Letteratura Italiana", "Tipologia C. Esempio di costruzione del tema", "Panzeri Maddalena"),
        Lesson(day(0), 2, 2, "Elettrotecnica ed Elettronica", "Induttore, induttanza e legge di Hopkinson", "Ricci Claudio"),
        Lesson(day(0), 4, 1, "Lingua Inglese", "Present perfect: esercizi", "Garato Giulia")
    )
    val agenda = listOf(
        Event(day(0), "12:00", "Elettrotecnica ed Elettronica", "Ricci Claudio", "Svolgere il test di verifica caricato su Classroom", true, 1),
        Event(day(0), "08:00", "Lingua Inglese", "Garato Giulia", "Pag. 127 es. 3", true, 2),
        Event(day(2), "", "Sistemi e Reti", "De Rito Mauro", "Verifica: circuiti di condizionamento e algoritmi", false, 3),
        Event(day(3), "", "Elettrotecnica ed Elettronica", "Ricci Claudio", "Interrogazione", false, 4)
    )
    val reminders = listOf(Reminder("1", "debug", day(0), "Portare il libro di disegno"))

    val absences = listOf(
        Absence(day(-3), "Ritardo", false, ""),
        Absence(day(-12), "Assenza", true, "Motivi di salute"),
        Absence(day(-20), "Assenza", true, "Motivi di famiglia"),
        Absence(day(-25), "Assenza parziale", true, ""),
        Absence(day(-30), "Uscita anticipata", true, "Visita medica")
    )
}

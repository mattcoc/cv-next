package io.github.mattcoc.cvnext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LogicTest {
    private fun grade(subject: String, value: Double?, counts: Boolean = true) =
        Grade(subject, "2026-10-01", value?.toString().orEmpty(), value, "Scritto", "", "Trimestre", 1, counts)

    @Test
    fun versionComparison() {
        assertTrue(Updater.isNewer("1.2.0", "1.1.1"))
        assertTrue(Updater.isNewer("1.1.1", "1.1"))
        assertTrue(Updater.isNewer("1.10", "1.9"))
        assertFalse(Updater.isNewer("1.1", "1.1.0"))
        assertFalse(Updater.isNewer("1.0", "1.1.1"))
    }

    @Test
    fun averagesIgnoreGradesThatDoNotCount() {
        val list = listOf(grade("Matematica", 6.0), grade("Matematica", 8.0), grade("Matematica", 2.0, counts = false))
        assertEquals(7.0, list.gradeAvg()!!, 1e-9)
    }

    @Test
    fun overallAverageIsTheAverageOfSubjects() {
        val list = listOf(grade("Matematica", 4.0), grade("Matematica", 6.0), grade("Storia", 8.0))
        assertEquals(6.5, list.subjectsAvg()!!, 1e-9)
    }

    @Test
    fun noGradesMeansNoAverage() {
        assertEquals(null, emptyList<Grade>().gradeAvg())
    }

    @Test
    fun neededGradeForTarget() {
        assertEquals(Needed.One(7.0), neededGrade(listOf(5.0), 6.0))
        assertEquals(Needed.Reached, neededGrade(listOf(7.0, 6.0), 6.0))
        assertEquals(Needed.One(6.0), neededGrade(emptyList(), 6.0))
        assertEquals(Needed.Two(9.0), neededGrade(listOf(3.0, 3.0), 6.0))
        assertEquals(Needed.Impossible, neededGrade(listOf(2.0, 2.0, 2.0, 2.0), 9.0))
    }

    @Test
    fun neededGradeRoundsUpToQuarter() {
        // (6 * 4) - (5 + 5.5 + 6) = 7.5
        assertEquals(Needed.One(7.5), neededGrade(listOf(5.0, 5.5, 6.0), 6.0))
        // (6 * 3) - (5 + 6.1) = 6.9 -> 7
        assertEquals(Needed.One(7.0), neededGrade(listOf(5.0, 6.1), 6.0))
    }

    @Test
    fun keepGradeStaysInRange() {
        assertEquals(1.0, keepGrade(listOf(10.0, 10.0), 6.0), 1e-9)
        assertEquals(4.0, keepGrade(listOf(7.0), 5.5), 1e-9)
    }

    @Test
    fun lastYearHostFollowsSchoolYear() {
        assertEquals("https://web25.spaggiari.eu", Endpoints.lastYearHost(LocalDate.of(2026, 10, 11)))
        assertEquals("https://web25.spaggiari.eu", Endpoints.lastYearHost(LocalDate.of(2027, 6, 1)))
        assertEquals("https://web26.spaggiari.eu", Endpoints.lastYearHost(LocalDate.of(2027, 9, 15)))
    }

    @Test
    fun prettyKeepsSmallWordsLowercase() {
        assertEquals("Lingua e Letteratura Italiana", "LINGUA E LETTERATURA ITALIANA".pretty())
    }

    @Test
    fun recognisesTests() {
        val yes = listOf(
            "Verifica scritta capitoli 3-4",
            "Interrogazione programmata",
            "Interrogati: Rossi, Bianchi, Verdi",
            "Compito in classe di matematica",
            "Compito di storia sul Risorgimento",
            "Compito: equazioni di secondo grado",
            "Prova scritta di italiano",
            "Simulazione della seconda prova",
            "Test d'ingresso di inglese",
            "Verifica di recupero per chi ha l'insufficienza"
        )
        val no = listOf(
            "Esercizi in preparazione al compito",
            "Ripassare per la verifica di giovedì",
            "Studiare il capitolo 3 per l'interrogazione",
            "Correzione della verifica",
            "Consegna della verifica corretta",
            "Svolgere il test di verifica caricato su classroom",
            "Compito per casa: pag. 12 es. 3",
            "Prova a risolvere gli esercizi da 1 a 5",
            "Uscita didattica al museo",
            "Pag. 127 es. 3 come suddiviso in classe",
            "Portare il materiale per la verifica di laboratorio"
        )
        yes.forEach { assertTrue("dovrebbe essere una verifica: $it", TestDetector.isTest(it)) }
        no.forEach { assertFalse("non dovrebbe essere una verifica: $it", TestDetector.isTest(it)) }
    }

    @Test
    fun findsMyName() {
        val names = Mentions.defaultNames("Matteo De Rossi")
        assertEquals(listOf("De Rossi"), names)
        assertTrue(Mentions.matches("Interrogazione: DE ROSSI, Bianchi", names))
        assertTrue(Mentions.matches("Interrogati: de rossi m., verdi", names))
        assertFalse(Mentions.matches("Interrogati: Rossi, Bianchi", names))
        assertTrue(Mentions.matches("Recupero per Nicolò", listOf("Nicolo")))
        assertFalse(Mentions.matches("Interrogazione: Rossini", listOf("Rossi")))
        assertFalse(Mentions.matches("Interrogazione: Al", listOf("Al")))
    }

    @Test
    fun nextSchoolDaySkipsSunday() {
        assertEquals(LocalDate.of(2026, 10, 12), Evening.nextSchoolDay(LocalDate.of(2026, 10, 10)))
        assertEquals(LocalDate.of(2026, 10, 13), Evening.nextSchoolDay(LocalDate.of(2026, 10, 12)))
        assertEquals(LocalDate.of(2026, 10, 12), Evening.nextSchoolDay(LocalDate.of(2026, 10, 11)))
    }

    @Test
    fun eventSubjectComesFromTheTeachersLessons() {
        val lessons = listOf(
            Lesson("2026-10-01", 1, 1, "Sistemi Automatici", "", "Galli Paolo"),
            Lesson("2026-10-01", 2, 1, "Elettrotecnica ed Elettronica", "", "Ricchi Claudio"),
            Lesson("2026-10-02", 2, 2, "Elettrotecnica ed Elettronica", "", "Ricchi Claudio"),
            Lesson("2026-10-03", 4, 1, "Laboratorio Tecnologico", "", "Ricchi Claudio, Bianchi Luca")
        )
        val t = Teachers(lessons)
        fun ev(author: String, notes: String, subject: String = "") = Event("2026-10-13", "", subject, author, notes, false)
        assertEquals("Sistemi Automatici", t.subject(ev("Galli Paolo", "Verifica")))
        assertEquals("Elettrotecnica ed Elettronica", t.subject(ev("Ricchi Claudio", "Interrogazione")))
        assertEquals("Laboratorio Tecnologico", t.subject(ev("Ricchi Claudio", "Verifica di laboratorio")))
        assertEquals("Storia", t.subject(ev("Ricchi Claudio", "", "Storia")))
        assertEquals("", t.subject(ev("Sconosciuto", "Verifica")))
    }

    @Test
    fun agendaTextIsTidied() {
        assertEquals("Verifica di sistemi: circuiti", "Verifica di sistemi  : circuiti".tidy())
        assertEquals("Interrogazione elettrotecnica: Casotto, Nardo", "INTERROGAZIONE ELETTROTECNICA: Casotto, Nardo".tidy())
        assertEquals("Portare il libro", "PORTARE IL LIBRO".tidy())
        assertEquals("pag 127 es 3", "pag 127 es 3".tidy())
    }

    @Test
    fun testKind() {
        assertEquals("Interrogazione", Event("2026-10-13", "", "", "", "INTERROGAZIONE: Rossi", false).testKind)
        assertEquals("Verifica", Event("2026-10-13", "", "", "", "Verifica unit 9", false).testKind)
    }
}

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
        fun ev(notes: String, homework: Boolean = false) = Event("2026-10-14", "", "Storia", "Rossi", notes, homework)
        assertTrue(ev("Verifica scritta capitoli 3-4").isTest)
        assertTrue(ev("Interrogazione programmata").isTest)
        assertTrue(ev("Compito in classe di matematica").isTest)
        assertFalse(ev("Uscita didattica al museo").isTest)
        assertFalse(ev("Svolgere il test di verifica", homework = true).isTest)
    }

    @Test
    fun nextSchoolDaySkipsSunday() {
        assertEquals(LocalDate.of(2026, 10, 12), Evening.nextSchoolDay(LocalDate.of(2026, 10, 10)))
        assertEquals(LocalDate.of(2026, 10, 13), Evening.nextSchoolDay(LocalDate.of(2026, 10, 12)))
        assertEquals(LocalDate.of(2026, 10, 12), Evening.nextSchoolDay(LocalDate.of(2026, 10, 11)))
    }
}

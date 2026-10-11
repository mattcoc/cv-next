package com.example.classevivamodernaemeglio

/** Dati di esempio usati solo dagli screenshot automatici della versione di sviluppo. */
object DebugData {
    val periods = listOf(
        Period(1, "Trimestre", "2026-09-01", "2026-12-31"),
        Period(3, "Pentamestre", "2027-01-01", "2027-06-30")
    )
    val grades = listOf(
        Grade("Lingua Inglese", "2026-10-08", "7½", 7.5, "Scritto", "", "Trimestre", 1, true, 1),
        Grade("Matematica", "2026-10-06", "8", 8.0, "Orale", "", "Trimestre", 1, true, 2),
        Grade("Matematica", "2026-09-29", "6½", 6.5, "Scritto", "", "Trimestre", 1, true, 3),
        Grade("Storia", "2026-10-02", "5", 5.0, "Orale", "", "Trimestre", 1, true, 4),
        Grade("Lingua e Letteratura Italiana", "2026-10-01", "+", null, "Orale", "", "Trimestre", 1, false, 5)
    )
}

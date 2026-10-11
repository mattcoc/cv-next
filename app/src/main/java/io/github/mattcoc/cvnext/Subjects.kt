package io.github.mattcoc.cvnext

/**
 * Classeviva spesso lascia vuota la materia degli eventi dell'agenda e mette solo il docente.
 * La materia si ricava dalle lezioni dello stesso docente; se ne insegna più di una vince quella
 * citata nel testo dell'evento, altrimenti quella con più lezioni.
 */
class Teachers(lessons: List<Lesson>) {
    private val subjects: Map<String, List<String>> = lessons
        .filter { it.subject.isNotBlank() }
        .flatMap { l -> l.author.split(',').map { it.plain().trim() }.filter(String::isNotBlank).map { it to l.subject } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, l) -> l.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }

    fun subject(e: Event): String {
        if (e.subject.isNotBlank()) return e.subject
        val options = subjects[e.author.plain().trim()].orEmpty()
        if (options.size <= 1) return options.firstOrNull().orEmpty()
        val notes = e.notes.plain()
        return options.firstOrNull { s -> s.plain().split(' ').any { it.length >= 5 && it.take(6) in notes } } ?: options.first()
    }
}

/** "Verifica" o "Interrogazione", per gli eventi riconosciuti come prova. */
val Event.testKind get() = if (Regex("\\binterrog").containsMatchIn(notes.plain())) "Interrogazione" else "Verifica"

/** Testo dell'agenda ripulito: spazi doppi tolti e frasi tutte in maiuscolo riportate in minuscolo. */
fun String.tidy(): String {
    val t = trim().replace(Regex("[ \\t]+([:;,.])"), "$1").replace(Regex("[ \\t]{2,}"), " ")
    val letters = t.filter(Char::isLetter)
    if (letters.length >= 8 && letters.count(Char::isUpperCase) > letters.length * 0.8) return t.lowercase().replaceFirstChar(Char::uppercase)
    // "INTERROGAZIONE ELETTROTECNICA: Rossi, Bianchi" → "Interrogazione elettrotecnica: Rossi, Bianchi"
    return t.replace(Regex("^([\\p{Lu}'’ ]{6,})(?=[:\\-–])")) { it.value.trim().lowercase().replaceFirstChar(Char::uppercase) }
}

package io.github.mattcoc.cvnext

import org.json.JSONObject
import android.text.Html
import java.io.File
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Grade(
    val subject: String, val date: String, val value: String, val decimal: Double?,
    val type: String, val notes: String, val period: String, val periodPos: Int, val counts: Boolean, val id: Long = 0
)
data class Event(
    val date: String, val time: String, val subject: String, val author: String, val notes: String,
    val homework: Boolean, val id: Long = 0, val cls: String = ""
)


data class Absence(val date: String, val type: String, val justified: Boolean, val reason: String)
data class Attachment(val name: String, val num: Int)
data class Notice(
    val title: String, val date: String, val read: Boolean, val category: String,
    val code: String, val id: Long, val attachments: List<Attachment>
)
data class Lesson(val date: String, val hour: Int, val hours: Int, val subject: String, val topic: String, val author: String, val cls: String = "")

private fun JSONObject.list(key: String) = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
private fun JSONObject.str(key: String) = if (isNull(key)) "" else optString(key)

private val small = setOf("e", "ed", "di", "del", "della", "dei", "delle", "da", "a", "in", "per", "con", "la", "il")

fun String.pretty() = lowercase().split(' ').mapIndexed { i, w -> if (i > 0 && w in small) w else w.replaceFirstChar(Char::uppercase) }.joinToString(" ")

suspend fun Api.grades() = get("/grades").list("grades").map {
    Grade(
        it.str("subjectDesc").pretty(), it.str("evtDate"), it.str("displayValue"),
        if (it.isNull("decimalValue")) null else it.getDouble("decimalValue"),
        it.str("componentDesc"), it.str("notesForFamily"), it.str("periodDesc"), it.optInt("periodPos"),
        it.str("color") != "blue" && !it.optBoolean("canceled"), it.optLong("evtId")
    )
}.sortedByDescending { it.date }

/** Anno scolastico in corso, da settembre ad agosto. */
fun schoolYear(today: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate> {
    val start = LocalDate.of(if (today.monthValue >= 9) today.year else today.year - 1, 9, 1)
    return start to start.plusYears(1).minusDays(1)
}

private fun JSONObject.event(): Event {
    val begin = str("evtDatetimeBegin")
    return Event(
        begin.take(10), if (optBoolean("isFullDay")) "" else begin.drop(11).take(5),
        str("subjectDesc").pretty(), str("authorName").pretty(), str("notes"),
        str("evtCode") == "AGHW" || !isNull("homeworkId"),
        optLong("evtId"), str("classDesc")
    )
}

/** Un compito della sezione "Compiti" del registro (homeworks), con la scadenza come data dell'evento. */
private fun JSONObject.homework() = Event(
    str("expiryDate").take(10), str("expiryTime").take(5), str("subjectDesc").pretty(), str("teacherName").pretty(),
    str("homeworkDesc"), true, optLong("evtId"), ""
)

private fun String.key() = plain().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().take(40)

/**
 * I compiti assegnati dalla sezione "Compiti" del registro (rest/w1 homeworks) non sono nell'agenda dell'app ufficiale
 * (rest/v1): si aggiungono quelli che scadono nel periodo, saltando quelli già presenti in agenda con lo stesso testo.
 */
suspend fun Api.agenda(
    from: LocalDate = LocalDate.now().minusDays(30), to: LocalDate = LocalDate.now().plusDays(30)
): List<Event> {
    val f = DateTimeFormatter.BASIC_ISO_DATE
    val events = get("/agenda/all/${from.format(f)}/${to.format(f)}").list("agenda").map { it.event() }
    val known = events.map { it.date + it.notes.key() }.toSet()
    val homework = runCatching { webGet("/homeworks/index").list("items").map { it.homework() } }.getOrDefault(emptyList())
        .filter { it.date.length == 10 && it.date >= from.toString() && it.date <= to.toString() && it.date + it.notes.key() !in known }
    return (events + homework).sortedBy { it.date + it.time }
}

suspend fun Api.absences() = get("/absences/details").list("events").map {
    val code = it.str("evtCode")
    val type = when {
        code == "ABA0" -> "Assenza"
        code.startsWith("ABA") -> "Assenza parziale"
        code.startsWith("ABU") -> "Uscita anticipata"
        else -> "Ritardo"
    }
    Absence(it.str("evtDate"), type, it.optBoolean("isJustified"), it.str("justifReasonDesc"))
}.sortedByDescending { it.date }

suspend fun Api.notices() = get("/noticeboard").list("items").map {
    Notice(
        it.str("cntTitle"), it.str("pubDT"), it.optBoolean("readStatus"), it.str("cntCategory"),
        it.str("evtCode"), it.optLong("pubId"),
        it.list("attachments").map { a -> Attachment(a.str("fileName"), a.optInt("attachNum", 1)) }
    )
}.sortedByDescending { it.date }

suspend fun Api.readNotice(n: Notice): String {
    val item = post("/noticeboard/read/${n.code}/${n.id}/101").optJSONObject("item") ?: return ""
    return Html.fromHtml(item.str("text"), Html.FROM_HTML_MODE_COMPACT).toString().trim()
}

suspend fun Api.attachment(dir: File, n: Notice, a: Attachment): File {
    dir.mkdirs()
    val file = File(dir, "notice_${n.id}_${a.num}.pdf")
    if (file.length() > 0) return file
    suspend fun download() = runCatching { bytes("/noticeboard/attach/${n.code}/${n.id}/${a.num}") }.getOrNull()
        ?.takeIf { it.isPdf() } ?: runCatching { bytes("/noticeboard/attach/${n.code}/${n.id}/101") }.getOrNull()?.takeIf { it.isPdf() }
    // Una circolare appena arrivata (ad esempio aperta da una notifica) dà 404 sugli allegati finché non risulta letta:
    // se il primo tentativo fallisce la segna come letta e riprova.
    val data = download() ?: run {
        runCatching { readNotice(n) }
        delay(800)
        download()
    } ?: throw ApiException("Allegato non ancora disponibile. Riprova tra qualche secondo.")
    file.writeBytes(data)
    return file
}

fun ByteArray.isPdf() = size > 4 && String(copyOfRange(0, 4)) == "%PDF"

data class Period(val pos: Int, val name: String, val start: String, val end: String) {
    val current get() = runCatching {
        val today = LocalDate.now().toString()
        val s = start.take(10)
        val e = end.take(10)
        s.length == 10 && e.length == 10 && today >= s && today <= e
    }.getOrDefault(false)
}

suspend fun Api.periods() = get("/periods").list("periods")
    .map { Period(it.optInt("periodPos"), it.str("periodDesc").pretty(), it.str("dateStart"), it.str("dateEnd")) }
    .filter { it.name.isNotBlank() }.distinctBy { it.pos }.sortedBy { it.pos }

data class School(val name: String, val city: String)

suspend fun Api.school(): School {
    val c = get("/card").optJSONObject("card") ?: JSONObject()
    return School(
        listOf(c.str("schName").pretty(), c.str("schDedication").replace("\"", "").trim().pretty())
            .filter(String::isNotBlank).joinToString(" ").replace(Regex("\\s+"), " "),
        listOf(c.str("schCity").pretty(), c.str("schProv").uppercase()).filter(String::isNotBlank).joinToString(" ")
    )
}

fun className(raw: String) = raw.substringBefore(' ').substringBefore('(').trim()

data class Payment(val id: String, val title: String, val amount: String, val due: String)

/** Una rata di PagOnline, pagata o no. */
data class Charge(val id: String, val title: String, val amount: String, val paid: String, val due: String, val settled: Boolean)

/** Tutte le rate di PagOnline: da pagare, scadute e già pagate. */
suspend fun Api.charges(): List<Charge> {
    val raw = webPage(Endpoints.payments(studentId()))
    val json = runCatching { JSONObject(raw) }.getOrNull()
        ?: throw ApiException("Impossibile accedere ai pagamenti online (sessione web scaduta)")
    return json.list("items").flatMap { e ->
        e.list("payPlan").map {
            val due = it.optDouble("amountDue", 0.0)
            val paid = it.optDouble("amountPaid", 0.0)
            Charge(
                "${it.optLong("payId")}",
                e.str("evtDesc").trim().lowercase().replaceFirstChar { c -> c.uppercase() },
                num(due), num(paid), it.str("deadlineDate").take(10), paid >= due && due > 0 || it.optInt("payStatus") == 2
            )
        }
    }.sortedBy { it.due }
}

private val sessionTabs = Regex("""<li><a href="#(S\d+)">(.*?)</a></li>""", RegexOption.DOT_MATCHES_ALL)
private val subjectRow = Regex(
    """<tr class="riga_materia_componente"[^>]*sessione="([^"]*)"[^>]*materia_id="[^"]*"(.*?)</tr>""", RegexOption.DOT_MATCHES_ALL
)
private val subjectName = Regex("""class="materia_desc"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
private val gradeCell = Regex(
    """<td class="registro cella_voto[^"]*"[^>]*componente="(\d+)"[^>]*evento_codice="[^"]*"[^>]*evento_id="([^"]*)"[^>]*>(.*?)</td>""",
    RegexOption.DOT_MATCHES_ALL
)
private val gradeDate = Regex("""class="voto_data cella_data">([^<]*)</span>""")
private val gradeValue = Regex("""class="([^"]*cella_div[^"]*)"[^>]*title="([^"]*)"[^>]*>\s*<p[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)

private fun htmlText(s: String) = android.text.Html.fromHtml(s, android.text.Html.FROM_HTML_MODE_LEGACY).toString().replace('\u00a0', ' ').trim()

/** "7½", "7+", "6-", "7 1/2" in numero; i giudizi a lettere restano senza valore. */
fun gradeNumber(raw: String): Double? {
    var v = raw.replace(" ", "").replace("1/2", "½")
    var delta = 0.0
    while (v.isNotEmpty() && v.last() in "½+-") {
        delta += when (v.last()) { '½' -> 0.5; '+' -> 0.25; else -> -0.25 }
        v = v.dropLast(1)
    }
    return v.replace(',', '.').toDoubleOrNull()?.plus(delta)
}

/**
 * Voti dell'anno precedente, letti dalla pagina del registro web di quell'anno
 * (l'API dell'app non li restituisce più a fine anno).
 */
suspend fun Api.lastYearGrades(): List<Grade> {
    val page = lastYearPage("/cvv/app/default/genitori_voti.php")
    val start = schoolYear().first.year - 1
    val periods = sessionTabs.findAll(page).associate { it.groupValues[1] to htmlText(it.groupValues[2]) }
    val types = mapOf("1" to "Scritto/Grafico", "2" to "Orale", "3" to "Pratico")
    return subjectRow.findAll(page).flatMap { row ->
        val session = row.groupValues[1]
        val body = row.groupValues[2]
        val subject = subjectName.find(body)?.let { htmlText(it.groupValues[1]) }.orEmpty().pretty()
        gradeCell.findAll(body).mapNotNull { cell ->
            if (cell.groupValues[2].isBlank()) return@mapNotNull null
            val content = cell.groupValues[3]
            val v = gradeValue.find(content) ?: return@mapNotNull null
            val value = htmlText(v.groupValues[3]).ifBlank { return@mapNotNull null }
            val short = gradeDate.find(content)?.let { htmlText(it.groupValues[1]) }.orEmpty()
            val date = short.split("/").mapNotNull { it.trim().toIntOrNull() }.takeIf { it.size >= 2 }?.let { (d, m) ->
                runCatching { LocalDate.of(if (m >= 9) start else start + 1, m, d).toString() }.getOrNull()
            }.orEmpty()
            Grade(
                subject, date, value, gradeNumber(value), types[cell.groupValues[1]] ?: v.groupValues[2],
                "", periods[session] ?: session, session.drop(1).toIntOrNull() ?: 0,
                "f_reg_voto_dettaglio" !in v.groupValues[1], cell.groupValues[2].toLongOrNull() ?: 0
            )
        }
    }.toList().sortedByDescending { it.date }
}

suspend fun Api.payments(): List<Payment> {
    val raw = webPage(Endpoints.payments(studentId()))
    val json = runCatching { JSONObject(raw) }.getOrNull()
        ?: throw ApiException("Impossibile accedere ai pagamenti online (sessione web scaduta)")
    val today = LocalDate.now().toString()
    return json.list("items").flatMap { e ->
        e.list("payPlan").filter {
            it.optInt("payStatus") == 1 && it.str("deadlineDate") >= today && it.optDouble("amountPaid", 0.0) < it.optDouble("amountDue", 0.0)
        }.map {
            Payment(
                "${it.optLong("payId")}",
                e.str("evtDesc").trim().lowercase().replaceFirstChar { c -> c.uppercase() },
                num(it.optDouble("amountDue")),
                it.str("deadlineDate")
            )
        }
    }.sortedBy { it.due }
}

suspend fun Api.lessons(from: LocalDate, to: LocalDate): List<Lesson> {
    val f = DateTimeFormatter.BASIC_ISO_DATE
    return get("/lessons/${from.format(f)}/${to.format(f)}").list("lessons").map {
        Lesson(it.str("evtDate"), it.optInt("evtHPos"), it.optInt("evtDuration", 1).coerceAtLeast(1),
            it.str("subjectDesc").pretty(), it.str("lessonArg"), it.str("authorName").pretty(), it.str("classDesc"))
    }.sortedWith(compareBy({ it.date }, { it.hour })).fold(mutableListOf<Lesson>()) { acc, l ->
        val p = acc.lastOrNull()
        if (p != null && p.date == l.date && p.subject == l.subject && p.topic == l.topic && p.hour + p.hours == l.hour)
            acc[acc.lastIndex] = p.copy(hours = p.hours + l.hours)
        else acc.add(l)
        acc
    }
}

private val ita = Locale.ITALIAN
private val longFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", ita)
private val shortFmt = DateTimeFormatter.ofPattern("d MMM", ita)
private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", ita)
val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", ita)

fun longDate(s: String) = runCatching { LocalDate.parse(s.take(10)).format(longFmt).replaceFirstChar { c -> c.uppercase() } }.getOrDefault(s)
fun shortDate(s: String) = runCatching { LocalDate.parse(s.take(10)).format(shortFmt) }.getOrDefault(s)
fun monthName(s: String) = runCatching { YearMonth.parse(s.take(7)).format(monthFmt).replaceFirstChar { c -> c.uppercase() } }.getOrDefault(s)

fun List<Double>.avg() = if (isEmpty()) null else average()
fun List<Grade>.gradeAvg() = filter { it.counts }.mapNotNull { it.decimal }.avg()
fun List<Grade>.subjectsAvg() = groupBy { it.subject }.values.mapNotNull { it.gradeAvg() }.avg()
fun num(x: Double) = String.format(ita, "%.2f", x)

/** Cosa serve per portare la media dei voti [values] almeno a [target]. */
sealed interface Needed {
    data object Reached : Needed
    data class One(val grade: Double) : Needed
    data class Two(val grade: Double) : Needed
    data object Impossible : Needed
}

private fun quarterUp(x: Double) = Math.ceil(x * 4 - 1e-9) / 4

/** Il voto minimo al prossimo compito per non scendere sotto [target]. */
fun keepGrade(values: List<Double>, target: Double) = quarterUp(target * (values.size + 1) - values.sum()).coerceIn(1.0, 10.0)

fun neededGrade(values: List<Double>, target: Double): Needed {
    val sum = values.sum()
    if (values.isNotEmpty() && sum / values.size >= target) return Needed.Reached
    fun round(x: Double) = quarterUp(x)
    val one = round(target * (values.size + 1) - sum)
    if (one <= 10) return Needed.One(one.coerceAtLeast(1.0))
    val two = round((target * (values.size + 2) - sum) / 2)
    return if (two <= 10) Needed.Two(two) else Needed.Impossible
}

data class NoteItem(val type: String, val date: String, val author: String, val text: String, val read: Boolean, val id: Long = 0, val code: String = "") {
    /** Classeviva manda il testo delle note non ancora lette come "...": si ottiene solo segnandole come lette. */
    val hidden get() = text.trim().trim('.', '…').isBlank()
}

suspend fun Api.notes(): List<NoteItem> {
    val o = get("/notes/all")
    val types = mapOf("NTCL" to "Nota disciplinare", "NTWN" to "Richiamo", "NTTE" to "Annotazione", "NTST" to "Sanzione disciplinare")
    return types.flatMap { (key, label) ->
        o.list(key).map { NoteItem(label, it.str("evtDate"), it.str("authorName").pretty(), it.str("evtText"), it.optBoolean("readStatus"), it.optLong("evtId"), key) }
    }.sortedByDescending { it.date }
}

suspend fun Api.readNote(n: NoteItem): String =
    post("/notes/${n.code}/read/${n.id}").optJSONObject("event")?.str("evtText").orEmpty().trim()

data class DidContent(val id: Long, val name: String, val type: String, val date: String)
data class DidFolder(val name: String, val date: String, val contents: List<DidContent>)
data class DidTeacher(val name: String, val folders: List<DidFolder>)

suspend fun Api.didactics() = get("/didactics").list("didacticts").map { t ->
    DidTeacher(t.str("teacherName").pretty(), t.list("folders").map { f ->
        DidFolder(f.str("folderName"), f.str("lastShareDT"), f.list("contents").map { c ->
            DidContent(c.optLong("contentId"), c.str("contentName"), c.str("objectType"), c.str("shareDT"))
        })
    }.sortedByDescending { it.date })
}.filter { it.folders.isNotEmpty() }.sortedBy { it.name }

sealed interface Opened {
    data class Pdf(val file: File) : Opened
    data class Other(val file: File) : Opened
    data class Link(val url: String) : Opened
    data class Text(val text: String) : Opened
}

suspend fun Api.didacticItem(dir: File, c: DidContent): Opened {
    dir.mkdirs()
    val data = bytes("/didactics/item/${c.id}")
    if (!data.isPdf()) runCatching { JSONObject(String(data)).getJSONObject("item") }.getOrNull()?.let { item ->
        item.str("link").takeIf { it.isNotBlank() }?.let { return Opened.Link(it) }
        item.str("text").takeIf { it.isNotBlank() }?.let { return Opened.Text(it) }
    }
    val ext = if (data.isPdf()) "pdf" else c.name.substringAfterLast('.', "bin").lowercase().take(5)
    val file = File(dir, "did_${c.id}.$ext").apply { writeBytes(data) }
    return if (data.isPdf()) Opened.Pdf(file) else Opened.Other(file)
}

data class SchoolDoc(val hash: String, val title: String)
data class SchoolReport(val title: String, val url: String)
data class Documents(val docs: List<SchoolDoc>, val reports: List<SchoolReport>)

suspend fun Api.documents(): Documents {
    val o = post("/documents")
    return Documents(
        o.list("documents").map { SchoolDoc(it.str("hash"), it.str("desc")) },
        o.list("schoolReports").map { SchoolReport(it.str("desc"), it.str("viewLink")) }
    )
}

suspend fun Api.document(dir: File, d: SchoolDoc): File {
    dir.mkdirs()
    val file = File(dir, "doc_${d.hash}.pdf")
    if (file.length() > 0) return file
    runCatching { post("/documents/check/${d.hash}") }
    val data = postBytes("/documents/read/${d.hash}")
    if (!data.isPdf()) throw ApiException("Documento non disponibile")
    return file.apply { writeBytes(data) }
}

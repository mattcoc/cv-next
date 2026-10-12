package io.github.mattcoc.cvnext

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private const val YEAR = -1

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GradesScreen(all: List<Grade>, apiPeriods: List<Period>) {
    val periods = remember(all, apiPeriods) {
        apiPeriods.ifEmpty {
            all.sortedBy { it.periodPos }.filter { it.period.isNotBlank() }.distinctBy { it.periodPos }
                .map { Period(it.periodPos, it.period.pretty(), "", "") }
        }
    }
    val start = periods.firstOrNull { it.current }?.pos ?: periods.lastOrNull { p -> all.any { it.periodPos == p.pos } }?.pos ?: YEAR
    var period by rememberSaveable { mutableIntStateOf(start) }
    var bySubject by rememberSaveable { mutableStateOf(true) }
    var open by remember { mutableStateOf<String?>(null) }
    fun of(pos: Int) = if (pos == YEAR) all else all.filter { it.periodPos == pos }
    fun name(pos: Int) = periods.firstOrNull { it.pos == pos }?.name ?: "Anno"
    val grades = of(period)
    val subjects = grades.groupBy { it.subject }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    val avg = grades.subjectsAvg()
    val tabs = periods.map { it.pos } + YEAR
    Screen {
        if (periods.size > 1) item {
            val card: @Composable (Int, Modifier) -> Unit = { pos, m ->
                PeriodCard(name(pos), of(pos).subjectsAvg(), of(pos).size, pos == period, m) { period = pos }
            }
            if (tabs.size <= 3) Row(Modifier.appear(0), horizontalArrangement = Arrangement.spacedBy(8.dp)) { tabs.forEach { card(it, Modifier.weight(1f)) } }
            else LazyRow(Modifier.fillMaxWidth().fadeEdges().appear(0), contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tabs) { card(it, Modifier.width(136.dp)) }
            }
        }
        item {
            val (cardBg, cardFg) = averageCardColors(avg)
            Card(Modifier.appear(1), colors = CardDefaults.cardColors(containerColor = cardBg, contentColor = cardFg)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (period == YEAR) "Media dell'anno" else "Media · ${name(period)}", style = MaterialTheme.typography.titleMedium)
                            val v = animatedValue(avg)
                            Text(if (avg == null) "–" else num(v.toDouble()), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                            Text("${grades.size} voti · ${subjects.size} materie", style = MaterialTheme.typography.labelLarge)
                        }
                        AverageRing(avg, 72.dp, label = false)
                    }
                    val averages = subjects.mapValues { it.value.gradeAvg() }.filterValues { it != null }
                    val low = averages.filterValues { it!! < 6 }.keys.toList()
                    if (averages.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${averages.size - low.size} su ${averages.size} materie sufficienti",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium
                        )
                        if (low.isNotEmpty()) Text(
                            "Da recuperare: " + low.joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().appear(2)) {
                listOf("Materie", "Recenti").forEachIndexed { i, label ->
                    SegmentedButton(bySubject == (i == 0), { bySubject = i == 0 }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                }
            }
        }
        if (grades.isEmpty()) item { Empty(if (period == YEAR) "Nessun voto quest'anno" else "Nessun voto nel ${name(period).lowercase()}") }
        if (bySubject) animItems(subjects.entries.toList(), "s-$period", 3) { (subject, list) ->
            Card({ open = subject }, Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AverageRing(list.gradeAvg())
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column {
                            Text(subject, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val subjectAvg = list.gradeAvg()
                            val note = when {
                                subjectAvg == null -> "Nessun voto che fa media"
                                subjectAvg < 6 -> "Da recuperare"
                                else -> null
                            }
                            note?.let {
                                Text(
                                    it, style = MaterialTheme.typography.labelMedium,
                                    color = if (subjectAvg == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), maxLines = 1) { list.take(5).forEach { GradeChip(it) } }
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else animItems(grades, "g-$period", 3) {
            Item(it.subject, listOf(shortDate(it.date), it.type).filter(String::isNotBlank).joinToString(" · "), it.notes, expandable = true, leading = { GradeCircle(it) })
        }
    }
    open?.let { subject -> SubjectSheet(subject, all.filter { it.subject == subject }, periods) { open = null } }
}

@Composable
fun PeriodCard(name: String, avg: Double?, count: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val (tone, _) = gradeColors(avg)
    Card(
        onClick, modifier,
        colors = CardDefaults.cardColors(containerColor = if (selected) c.secondaryContainer else c.surfaceContainerHigh),
        border = if (selected) BorderStroke(2.dp, c.primary) else null
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                name, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, color = LocalContentColor.current),
                maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = 6.dp).size(8.dp).clip(CircleShape).background(if (avg == null) c.outlineVariant else tone))
                Text(avg?.let(::num) ?: "–", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text(if (count == 1) "1 voto" else "$count voti", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectSheet(subject: String, list: List<Grade>, periods: List<Period>, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val groups = list.groupBy { it.periodPos }.toSortedMap()
    ModalBottomSheet(onDismiss) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.padding(start = 4.dp, bottom = 4.dp).appear(0), verticalAlignment = Alignment.CenterVertically) {
                    AverageRing(list.gradeAvg(), 64.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(subject, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Media dell'anno · ${if (list.size == 1) "1 voto" else "${list.size} voti"}", color = c.onSurfaceVariant)
                    }
                }
            }
            item { Box(Modifier.appear(1)) { TrendCard(list) } }
            item { Box(Modifier.appear(1)) { TargetCard(list, periods) } }
            if (groups.size > 1) item {
                Row(Modifier.horizontalScroll(rememberScrollState()).appear(1), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { (pos, g) ->
                        val (bg, fg) = gradeColors(g.gradeAvg())
                        Row(
                            Modifier.clip(CircleShape).background(c.surfaceContainerHigh).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(periods.firstOrNull { it.pos == pos }?.name ?: g.first().period.pretty(), style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(8.dp))
                            Text(g.gradeAvg()?.let(::num) ?: "–", Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
                                color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            groups.entries.forEachIndexed { i, (pos, g) ->
                item("p-$pos") {
                    Box(Modifier.appear(i + 2)) {
                        Header("${periods.firstOrNull { it.pos == pos }?.name ?: g.first().period.pretty()} · media ${g.gradeAvg()?.let(::num) ?: "–"}")
                    }
                }
                animItems(g, "d-$pos", i + 3) { Item(it.type.ifBlank { "Voto" }, longDate(it.date), it.notes, expandable = true, leading = { GradeCircle(it) }) }
            }
        }
    }
}

/** "Quanto devo prendere?": il voto che serve nel periodo in corso per arrivare alla media scelta. */
@Composable
fun TargetCard(list: List<Grade>, periods: List<Period>) {
    val c = MaterialTheme.colorScheme
    val pos = periods.firstOrNull { it.current }?.pos ?: list.maxOfOrNull { it.periodPos } ?: return
    val values = list.filter { it.periodPos == pos && it.counts }.mapNotNull { it.decimal }
    val periodName = periods.firstOrNull { it.pos == pos }?.name ?: list.firstOrNull { it.periodPos == pos }?.period?.pretty().orEmpty()
    var target by rememberSaveable { mutableIntStateOf(6) }
    Card(colors = CardDefaults.cardColors(containerColor = c.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Quanto devo prendere?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            ChipRow(listOf(6, 7, 8, 9), target, { "Media $it" }) { target = it }
            val text = when (val n = neededGrade(values, target.toDouble())) {
                Needed.Reached -> "Hai già almeno $target di media${if (periodName.isNotBlank()) " nel $periodName" else ""}. Per restarci, al prossimo voto basta un ${num(keepGrade(values, target.toDouble()))}."
                is Needed.One -> "Al prossimo voto ti serve almeno ${num(n.grade)} per arrivare a $target."
                is Needed.Two -> "Con un solo voto non basta: servono due voti da almeno ${num(n.grade)}."
                Needed.Impossible -> "Per arrivare a $target servono più di due voti alti."
            }
            Text(text, color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Andamento dei voti che fanno media: punti per i voti, linea per la media man mano, riga tratteggiata al 6. */
@Composable
fun TrendCard(list: List<Grade>) {
    val points = list.filter { it.counts && it.decimal != null }.sortedBy { it.date }
    if (points.size < 2) return
    val c = MaterialTheme.colorScheme
    val averages = points.indices.map { i -> points.subList(0, i + 1).mapNotNull { it.decimal }.average() }
    var picked by remember(points) { mutableIntStateOf(points.lastIndex) }
    val dotColors = points.map { gradeColors(it.decimal).first }
    val lineColor = c.primary
    val grid = c.outlineVariant.copy(alpha = 0.6f)
    val passLine = c.outline
    val ring = c.surfaceContainerHigh
    val low = (points.minOf { it.decimal!! }.coerceAtMost(averages.min()) - 1).coerceAtLeast(1.0).toInt().toDouble()
    val high = 10.0
    Card(colors = CardDefaults.cardColors(containerColor = ring)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Andamento", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Box(Modifier.size(8.dp).clip(CircleShape).background(c.onSurfaceVariant))
                Text(" Voti   ", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                Box(Modifier.size(width = 14.dp, height = 2.dp).background(lineColor))
                Text(" Media   ", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                Box(Modifier.size(width = 14.dp, height = 2.dp).background(c.outline))
                Text(" 6", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
            }
            val g = points[picked]
            Text(
                "${g.value} · ${g.type.ifBlank { "Voto" }} · ${shortDate(g.date)} · media ${num(averages[picked])}",
                style = MaterialTheme.typography.bodyMedium, color = c.onSurface
            )
            Canvas(
                Modifier.fillMaxWidth().height(140.dp).pointerInput(points) {
                    detectTapGestures { offset ->
                        val step = if (points.size > 1) (size.width - 16.dp.toPx()) / (points.size - 1) else 0f
                        picked = ((offset.x - 8.dp.toPx()) / step).roundToInt().coerceIn(0, points.lastIndex)
                    }
                }
            ) {
                val pad = 8.dp.toPx()
                val w = size.width - 2 * pad
                val h = size.height - 2 * pad
                fun x(i: Int) = pad + w * i / (points.size - 1)
                fun y(v: Double) = pad + (h * (1 - (v - low) / (high - low))).toFloat()
                for (v in listOf(4.0, 6.0, 8.0, 10.0).filter { it >= low }) {
                    val pass = v == 6.0
                    drawLine(
                        if (pass) passLine else grid, Offset(pad, y(v)), Offset(size.width - pad, y(v)),
                        strokeWidth = (if (pass) 1.5f else 1f).dp.toPx(),
                        pathEffect = if (pass) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null
                    )
                }
                val path = Path().apply {
                    averages.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
                }
                drawPath(path, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                points.forEachIndexed { i, p ->
                    val center = Offset(x(i), y(p.decimal!!))
                    val r = if (i == picked) 7.dp.toPx() else 5.dp.toPx()
                    drawCircle(ring, r + 2.dp.toPx(), center)
                    drawCircle(dotColors[i], r, center)
                }
            }
            Row {
                Text(shortDate(points.first().date), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                Text("Tocca un punto per i dettagli", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                Text(shortDate(points.last().date), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant, textAlign = TextAlign.End)
            }
        }
    }
}

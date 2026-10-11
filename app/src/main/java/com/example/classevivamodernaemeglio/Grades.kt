package com.example.classevivamodernaemeglio

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Card
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

@OptIn(ExperimentalMaterial3Api::class)
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
            Card(Modifier.appear(1), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (period == YEAR) "Media dell'anno" else "Media · ${name(period)}", style = MaterialTheme.typography.titleMedium)
                        val v = animatedValue(avg)
                        Text(if (avg == null) "–" else num(v.toDouble()), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                        Text("${grades.size} voti · ${subjects.size} materie", style = MaterialTheme.typography.labelLarge)
                    }
                    AverageRing(avg, 72.dp)
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
                        Text(subject, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { list.take(5).forEach { GradeChip(it) } }
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
            Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

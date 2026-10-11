package com.example.classevivamodernaemeglio

import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.IconButton
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun Message(text: String, retry: (() -> Unit)? = null) = LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = PaddingValues(32.dp, 64.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp)
) {
    item { IconCircle(Icons.Rounded.CloudOff, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Modifier.appear(0)) }
    item { Text(text, Modifier.fillMaxWidth().appear(1), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    retry?.let { item { FilledTonalButton(it, Modifier.appear(2)) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Riprova") } } }
}

@Composable
fun LoadingState() = LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = PaddingValues(32.dp, 96.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp)
) {
    item { CircularProgressIndicator() }
    item { Text("Caricamento…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
fun Screen(content: LazyListScope.() -> Unit) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, bottom + 104.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

fun <T> LazyListScope.animItems(list: List<T>, key: String, start: Int = 0, content: @Composable (T) -> Unit) =
    itemsIndexed(list, { i, _ -> "$key-$i" }) { i, t -> Box(Modifier.appear(start + i)) { content(t) } }

@Composable
fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, modifier: Modifier = Modifier, onSelect: (T) -> Unit) = LazyRow(
    modifier.fillMaxWidth().fadeEdges(),
    contentPadding = PaddingValues(horizontal = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    items(options) { o -> FilterChip(o == selected, { onSelect(o) }, { Text(label(o)) }) }
}

@Composable
fun Header(text: String, action: String? = null, onAction: () -> Unit = {}) = Row(
    Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    action?.let { TextButton(onAction) { Text(it) } }
}

@Composable
fun Empty(text: String) = Card(Modifier.fillMaxWidth()) {
    Text(text, Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun Item(
    headline: String,
    overline: String? = null,
    supporting: String? = null,
    bold: Boolean = false,
    expandable: Boolean = false,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val body: @Composable () -> Unit = {
        ListItem(
            headlineContent = { Text(headline, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium) },
            overlineContent = overline?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
            supportingContent = supporting?.takeIf { it.isNotBlank() }?.let { { if (expandable) ExpandableText(it) else Text(it) } },
            leadingContent = leading,
            trailingContent = trailing,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
    if (onClick != null) Card(onClick, Modifier.fillMaxWidth()) { body() } else Card(Modifier.fillMaxWidth()) { body() }
}

@Composable
fun Circle(text: String, bg: Color, fg: Color, size: Dp = 44.dp) = Box(
    Modifier.size(size).clip(CircleShape).background(bg),
    contentAlignment = Alignment.Center
) { Text(text, color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge) }

@Composable
fun IconCircle(icon: ImageVector, bg: Color, fg: Color, modifier: Modifier = Modifier) = Box(
    modifier.size(44.dp).clip(CircleShape).background(bg),
    contentAlignment = Alignment.Center
) { Icon(icon, null, tint = fg) }

@Composable
fun gradeColors(value: Double?, counts: Boolean = true): Pair<Color, Color> {
    val dark = isSystemInDarkTheme()
    val c = MaterialTheme.colorScheme
    return when {
        !counts -> if (dark) Color(0xFF9ECAFF) to Color(0xFF003258) else Color(0xFF1565C0) to Color.White
        value == null -> c.surfaceVariant to c.onSurfaceVariant
        value >= 6 -> if (dark) Color(0xFF8ED88E) to Color(0xFF00390F) else Color(0xFF2E7D32) to Color.White
        else -> c.error to c.onError
    }
}

/** Colori della scheda della media: dipendono dal voto, non dal colore del tema (un tema rosso non deve sembrare un'insufficienza). */
@Composable
fun averageCardColors(avg: Double?): Pair<Color, Color> {
    val dark = isSystemInDarkTheme()
    val c = MaterialTheme.colorScheme
    return when {
        avg == null -> c.surfaceContainerHigh to c.onSurface
        avg >= 6 -> if (dark) Color(0xFF1D3A21) to Color(0xFFC4EEC2) else Color(0xFFD5F2D1) to Color(0xFF0B3A12)
        else -> c.errorContainer to c.onErrorContainer
    }
}

@Composable
fun GradeCircle(g: Grade, size: Dp = 44.dp) {
    val (bg, fg) = gradeColors(g.decimal, g.counts)
    Circle(g.value, bg, fg, size)
}

@Composable
fun GradeChip(g: Grade) {
    val (bg, fg) = gradeColors(g.decimal, g.counts)
    Text(
        g.value, Modifier.clip(MaterialTheme.shapes.small).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge
    )
}

@Composable
fun AverageRing(avg: Double?, size: Dp = 56.dp, label: Boolean = true) = Box(Modifier.size(size), contentAlignment = Alignment.Center) {
    val (color, _) = gradeColors(avg)
    val v = animatedValue(avg)
    CircularProgressIndicator(
        progress = { v / 10f },
        modifier = Modifier.fillMaxSize(),
        color = color,
        trackColor = color.copy(alpha = 0.2f),
        strokeWidth = 5.dp,
        strokeCap = StrokeCap.Round
    )
    if (label) Text(if (avg == null) "–" else num(v.toDouble()), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
}

@Composable
fun DayStrip(days: List<LocalDate>, selected: LocalDate, marked: (LocalDate) -> Boolean, onSelect: (LocalDate) -> Unit) {
    val state = rememberLazyListState((days.indexOf(selected) - 2).coerceAtLeast(0))
    val c = MaterialTheme.colorScheme
    LaunchedEffect(selected) {
        val i = days.indexOf(selected)
        if (i < 0) return@LaunchedEffect
        val info = snapshotFlow { state.layoutInfo }.first { it.visibleItemsInfo.isNotEmpty() }
        val item = info.visibleItemsInfo.first().size
        state.animateScrollToItem(i, -((info.viewportSize.width - item) / 2 - info.beforeContentPadding))
    }
    LazyRow(
        Modifier.fillMaxWidth().fadeEdges(), state = state,
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(days) { d ->
            val sel = d == selected
            val bg by animateColorAsState(if (sel) c.primary else c.surfaceContainerHigh, label = "bg")
            val fg by animateColorAsState(if (sel) c.onPrimary else c.onSurface, label = "fg")
            val scale by animateFloatAsState(if (sel) 1.06f else 1f, spring(0.45f, 500f), label = "scale")
            Column(
                Modifier.scale(scale).width(54.dp).clip(MaterialTheme.shapes.medium).background(bg)
                    .clickable { onSelect(d) }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(d.format(dayFmt), style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.75f))
                Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = fg)
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (!marked(d)) Color.Transparent else if (sel) c.onPrimary else c.primary))
            }
        }
    }
}

@Composable
fun DayPager(days: List<LocalDate>, selected: LocalDate, onSelect: (LocalDate) -> Unit, content: @Composable (LocalDate) -> Unit) {
    val pager = rememberPagerState(days.indexOf(selected).coerceAtLeast(0)) { days.size }
    LaunchedEffect(selected) {
        val i = days.indexOf(selected)
        if (i >= 0 && i != pager.currentPage) pager.animateScrollToPage(i)
    }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { onSelect(days[it]) } }
    HorizontalPager(pager, Modifier.fillMaxWidth(), pageSpacing = 16.dp, verticalAlignment = Alignment.Top) { content(days[it]) }
}

@Composable
fun LessonItem(l: Lesson) {
    val c = MaterialTheme.colorScheme
    val hours = if (l.hours > 1) "${l.hour}-${l.hour + l.hours - 1}" else "${l.hour}ª"
    Item(l.subject, l.author, l.topic, expandable = true, leading = { Circle(hours, c.primaryContainer, c.onPrimaryContainer) })
}

@Composable
fun EventItem(e: Event, showDate: Boolean = false) {
    val c = MaterialTheme.colorScheme
    val who = e.subject.ifBlank { e.author }
    val kind = if (e.homework) "Compito" else null
    Item(
        who.ifBlank { kind ?: "Evento" },
        listOfNotNull(
            if (showDate) shortDate(e.date) else null,
            e.time.ifBlank { "Tutto il giorno" },
            kind,
            if (e.subject.isNotBlank()) e.author else null
        ).filter(String::isNotBlank).joinToString(" · "),
        e.notes, expandable = true,
        leading = {
            if (e.homework) IconCircle(Icons.Rounded.EditNote, c.tertiaryContainer, c.onTertiaryContainer)
            else IconCircle(Icons.Rounded.Event, c.secondaryContainer, c.onSecondaryContainer)
        }
    )
}

@Composable
fun TodayScreen(
    name: String, school: School?, lessons: List<Lesson>, grades: List<Grade>, agenda: List<Event>, absences: List<Absence>,
    payments: List<Payment>, open: (Section) -> Unit, onPayments: () -> Unit,
    update: Release?, onUpdate: () -> Unit, onDismissUpdate: () -> Unit, onAbsences: () -> Unit
) {
    val today = remember { LocalDate.now() }
    val days = remember { (14L downTo 0L).map { today.minusDays(it) }.filter { it.dayOfWeek != DayOfWeek.SUNDAY } }
    val byDay = remember(lessons) { lessons.groupBy { it.date } }
    var selected by rememberSaveable { mutableStateOf(if (today.toString() in byDay) today.toString() else byDay.keys.maxOrNull() ?: today.toString()) }
    val pending = absences.count { !it.justified }
    Screen {
        item {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp).appear(0)) {
                Text("Ciao ${name.substringBefore(' ').pretty()}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(longDate(today.toString()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                val cls = className((lessons.lastOrNull { it.cls.isNotBlank() }?.cls ?: agenda.firstOrNull { it.cls.isNotBlank() }?.cls).orEmpty())
                val info = listOf(cls, school?.name.orEmpty()).filter(String::isNotBlank).joinToString(" · ")
                if (info.isNotBlank()) Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.School, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(info, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        update?.let { r ->
            item {
                Card(onUpdate, Modifier.fillMaxWidth().appear(1), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(16.dp, 8.dp, 4.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.SystemUpdate, null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Nuova versione ${r.version}", fontWeight = FontWeight.SemiBold)
                            Text("Tocca per aggiornare l'app", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onDismissUpdate) { Icon(Icons.Rounded.Close, "Ignora") }
                    }
                }
            }
        }
        if (pending > 0) item {
            Card(
                onAbsences, Modifier.fillMaxWidth().appear(1),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null)
                    Spacer(Modifier.width(12.dp))
                    Text(if (pending == 1) "1 evento da giustificare" else "$pending eventi da giustificare", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }
        }
        if (payments.isNotEmpty()) item {
            Card(
                onPayments, Modifier.fillMaxWidth().appear(1),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CreditCard, null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (payments.size == 1) "1 pagamento da fare" else "${payments.size} pagamenti da fare", fontWeight = FontWeight.SemiBold)
                        payments.first().let {
                            Text("${it.title} · € ${it.amount} entro il ${shortDate(it.due)}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }
        }
        item { Box(Modifier.appear(2)) { Header("Lezioni") } }
        item { Box(Modifier.appear(3)) { DayStrip(days, LocalDate.parse(selected), { byDay.containsKey(it.toString()) }) { selected = it.toString() } } }
        item {
            Box(Modifier.appear(4)) {
                DayPager(days, LocalDate.parse(selected), { selected = it.toString() }) { day ->
                    val list = byDay[day.toString()].orEmpty()
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DayLabel(day)
                        if (list.isEmpty()) Empty("Nessuna lezione registrata")
                        list.forEach { LessonItem(it) }
                    }
                }
            }
        }
        item { Box(Modifier.appear(5)) { Header("Ultimi voti", "Tutti") { open(Section.Voti) } } }
        if (grades.isEmpty()) item { Empty("Nessun voto") }
        else item {
            LazyRow(Modifier.fadeEdges().appear(6), contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(grades.take(10)) { i, g ->
                    Card(Modifier.width(124.dp).appear(6 + i)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GradeCircle(g)
                            Text(g.subject, style = MaterialTheme.typography.titleSmall, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(shortDate(g.date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item { Box(Modifier.appear(7)) { Header("In arrivo", "Agenda") { open(Section.Agenda) } } }
        val upcoming = agenda.filter { it.date >= today.toString() }
        if (upcoming.isEmpty()) item { Empty("Niente in programma") }
        animItems(upcoming.take(4), "agenda", 8) { EventItem(it, showDate = true) }
    }
}

@Composable
fun AgendaScreen(events: List<Event>, filter: String, onFilter: (String) -> Unit) {
    val today = remember { LocalDate.now() }
    val days = remember { (-30L..30L).map { today.plusDays(it) } }
    var selected by rememberSaveable { mutableStateOf(today.toString()) }
    val byDay = remember(events) { events.groupBy { it.date } }
    val homework = events.filter { it.homework && it.date >= today.toString() }
    val pastHomework = events.filter { it.homework && it.date < today.toString() }.sortedByDescending { it.date }
    val filters = listOf("Tutto", "Compiti")
    Screen {
        item {
            ChipRow(filters, filter, { if (it == "Compiti") "Compiti · ${homework.size}" else it }, Modifier.appear(0), onFilter)
        }
        if (filter == "Tutto") {
            item { Box(Modifier.appear(1)) { DayStrip(days, LocalDate.parse(selected), { byDay.containsKey(it.toString()) }) { selected = it.toString() } } }
            item {
                Box(Modifier.appear(2)) {
                    DayPager(days, LocalDate.parse(selected), { selected = it.toString() }) { day ->
                        val list = byDay[day.toString()].orEmpty()
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DayLabel(day)
                            if (list.isEmpty()) Empty("Niente in programma")
                            list.forEach { EventItem(it) }
                        }
                    }
                }
            }
            val next = events.filter { it.date > selected && it.date > today.toString() }
            if (next.isNotEmpty()) item { Box(Modifier.appear(3)) { Header("Prossimi giorni") } }
            animItems(next, "next-$selected", 4) { EventItem(it, showDate = true) }
        } else {
            if (homework.isEmpty()) item { Empty("Nessun compito nei prossimi 30 giorni") }
            homework.groupBy { it.date }.entries.forEachIndexed { i, (date, l) ->
                item("h-$filter-$date") { Box(Modifier.appear(i + 1)) { Header(longDate(date)) } }
                animItems(l, "f-$filter-$date", i + 2) { EventItem(it) }
            }
            if (pastHomework.isNotEmpty()) {
                item("past-header") { Header("Compiti passati") }
                animItems(pastHomework, "past-$filter") { EventItem(it, showDate = true) }
            }
        }
    }
}

@Composable
fun AbsencesScreen(list: List<Absence>) {
    val c = MaterialTheme.colorScheme
    val style = mapOf(
        "Assenza" to Triple("A", c.errorContainer, c.onErrorContainer),
        "Ritardo" to Triple("R", c.tertiaryContainer, c.onTertiaryContainer),
        "Uscita anticipata" to Triple("U", c.secondaryContainer, c.onSecondaryContainer)
    )
    Screen {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Assenze" to "Assenza", "Ritardi" to "Ritardo", "Uscite" to "Uscita anticipata").forEachIndexed { i, (label, type) ->
                    val (_, bg, fg) = style.getValue(type)
                    val n = animatedValue(list.count { it.type == type }.toDouble())
                    Card(Modifier.weight(1f).appear(i), colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${Math.round(n)}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                            Text(label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        if (list.isEmpty()) item { Empty("Nessuna assenza") }
        var index = 3
        list.groupBy { it.date.take(7) }.forEach { (month, events) ->
            val headerIndex = index
            item { Box(Modifier.appear(headerIndex)) { Header(monthName(month)) } }
            animItems(events, month, index + 1) {
                val (letter, bg, fg) = style.getValue(it.type)
                Item(
                    it.type, longDate(it.date),
                    if (it.justified) listOf("Giustificata", it.reason).filter(String::isNotBlank).joinToString(" · ") else "Da giustificare",
                    leading = { Circle(letter, bg, fg) },
                    trailing = if (it.justified) null else { { Icon(Icons.Default.Warning, null, tint = c.error) } }
                )
            }
            index += events.size + 1
        }
    }
}

@Composable
fun NoticesScreen(list: List<Notice>, onOpen: (Notice) -> Unit) {
    val categories = remember(list) { listOf("Tutte") + list.map { it.category }.filter(String::isNotBlank).distinct() }
    var filter by rememberSaveable { mutableStateOf("Tutte") }
    val c = MaterialTheme.colorScheme
    val shown = list.filter { filter == "Tutte" || it.category == filter }
    Screen {
        if (categories.size > 2) item { ChipRow(categories, filter, { it }, Modifier.appear(0)) { filter = it } }
        if (shown.isEmpty()) item { Empty("Nessuna comunicazione") }
        animItems(shown, "n-$filter", 1) {
            Item(
                it.title, listOf(shortDate(it.date), it.category).filter(String::isNotBlank).joinToString(" · "),
                when (it.attachments.size) {
                    0 -> null
                    1 -> "1 PDF allegato"
                    else -> "${it.attachments.size} PDF allegati"
                },
                bold = !it.read, onClick = { onOpen(it) },
                leading = { Box(Modifier.size(10.dp).clip(CircleShape).background(if (it.read) c.outlineVariant else c.primary)) },
                trailing = { AttachBadge(it.attachments.size) }
            )
        }
    }
}

@Composable
fun AttachBadge(count: Int) {
    val c = MaterialTheme.colorScheme
    when {
        count > 1 -> Row(
            Modifier.clip(CircleShape).background(c.tertiaryContainer).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.AttachFile, null, Modifier.size(16.dp), tint = c.onTertiaryContainer)
            Text("$count", color = c.onTertiaryContainer, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
        count == 1 -> Icon(Icons.Rounded.PictureAsPdf, "PDF", tint = c.primary)
        else -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.onSurfaceVariant)
    }
}

@Composable
fun DayLabel(day: LocalDate) = Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    val c = MaterialTheme.colorScheme
    Text(longDate(day.toString()), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Icon(Icons.Rounded.Swipe, null, Modifier.size(16.dp), tint = c.onSurfaceVariant)
    Spacer(Modifier.width(4.dp))
    Text("scorri", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
}

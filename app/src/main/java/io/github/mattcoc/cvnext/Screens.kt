package io.github.mattcoc.cvnext

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.ui.graphics.luminance
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

/** Vero se il tema dell'app è scuro: segue la scelta nelle impostazioni, non solo quella del telefono. */
fun androidx.compose.material3.ColorScheme.isDark() = surface.luminance() < 0.5f

@Composable
fun gradeColors(value: Double?, counts: Boolean = true): Pair<Color, Color> {
    val dark = MaterialTheme.colorScheme.isDark()
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
    val dark = MaterialTheme.colorScheme.isDark()
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
    val icon = gradeTypeIcon(g.type)
    Row(
        Modifier.clip(MaterialTheme.shapes.small).background(bg).padding(start = 10.dp, end = if (icon != null) 8.dp else 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(g.value, color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        icon?.let { Icon(it, g.type, Modifier.size(14.dp), tint = fg) }
    }
}

/** Icona del tipo di prova: scritto, orale o pratico. */
fun gradeTypeIcon(type: String): ImageVector? {
    val t = type.lowercase()
    return when {
        "oral" in t -> Icons.Rounded.RecordVoiceOver
        "scritt" in t || "grafic" in t -> Icons.Rounded.EditNote
        "pratic" in t || "laborator" in t -> Icons.Rounded.Build
        else -> null
    }
}

/** Lettera e colori di ogni tipo di assenza, usati nell'elenco e nella giornata. */
@Composable
fun absenceStyle(type: String): Triple<String, Color, Color> {
    // Colori fissi, uguali con ogni tema: rosso, arancio, blu e giallo come nel registro.
    val dark = MaterialTheme.colorScheme.isDark()
    fun pick(light: Long, onLight: Long, darkBg: Long, onDark: Long) = if (dark) Color(darkBg) to Color(onDark) else Color(light) to Color(onLight)
    val (letter, colors) = when (type) {
        "Assenza" -> "A" to pick(0xFFFFDAD6, 0xFF410002, 0xFF93000A, 0xFFFFDAD6)
        "Assenza parziale" -> "P" to pick(0xFFD1E4FF, 0xFF001D36, 0xFF004A77, 0xFFD1E4FF)
        "Uscita anticipata" -> "U" to pick(0xFFFFE08A, 0xFF241A00, 0xFF574500, 0xFFFFE08A)
        else -> "R" to pick(0xFFFFDCC2, 0xFF2E1500, 0xFF6E3900, 0xFFFFDCC2)
    }
    return Triple(letter, colors.first, colors.second)
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
    val length = if (l.hours == 1) "1 ora" else "${l.hours} ore"
    Item(l.subject, listOf(l.author, length).filter(String::isNotBlank).joinToString(" · "), l.topic, expandable = true, leading = { Circle(hours, c.primaryContainer, c.onPrimaryContainer) })
}

@Composable
fun EventItem(e: Event, showDate: Boolean = false) {
    val c = MaterialTheme.colorScheme
    val who = e.subject.ifBlank { e.author }
    val kind = if (e.homework) "Compito" else if (e.isTest) "Verifica" else null
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
    update: Release?, onUpdate: () -> Unit, onDismissUpdate: () -> Unit,
    reminders: List<Reminder> = emptyList(), onAddReminder: (String, String) -> Unit = { _, _ -> }, onRemoveReminder: (Reminder) -> Unit = {},
    onAbsences: () -> Unit
) {
    var adding by remember { mutableStateOf<LocalDate?>(null) }
    val today = remember { LocalDate.now() }
    val days = remember { (-14L..14L).map { today.plusDays(it) }.filter { it.dayOfWeek != DayOfWeek.SUNDAY } }
    val busy = remember(lessons, agenda, grades, absences, reminders) {
        (lessons.map { it.date } + agenda.map { it.date } + grades.map { it.date.take(10) } + absences.map { it.date.take(10) } + reminders.map { it.date }).toSet()
    }
    var selected by rememberSaveable {
        mutableStateOf(days.firstOrNull { it >= today }?.toString() ?: today.toString())
    }
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
        val tests = agenda.filter { it.isTest && it.date >= today.toString() && it.date <= today.plusDays(14).toString() }
        if (tests.isNotEmpty()) {
            item { Box(Modifier.appear(2)) { Header("Verifiche in arrivo") } }
            animItems(tests.take(3), "tests", 2) { TestItem(it, today) }
        }
        item { Box(Modifier.appear(2)) { Header("La giornata") } }
        item { Box(Modifier.appear(3)) { DayStrip(days, LocalDate.parse(selected), { it.toString() in busy }) { selected = it.toString() } } }
        item {
            Box(Modifier.appear(4)) {
                DayPager(days, LocalDate.parse(selected), { selected = it.toString() }) { day ->
                    DayContent(
                        day, today, lessons, agenda, grades, absences,
                        reminders.filter { it.date == day.toString() }, { adding = day }, onRemoveReminder
                    )
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
        item {
            Box(Modifier.appear(7)) {
                Item(
                    "Agenda completa", null, "Compiti ed eventi dei prossimi 30 giorni e compiti passati",
                    onClick = { open(Section.Agenda) },
                    leading = { IconCircle(Icons.Rounded.CalendarMonth, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) },
                    trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                )
            }
        }
    }
    adding?.let { day -> ReminderDialog(day, { adding = null }) { text -> onAddReminder(day.toString(), text); adding = null } }
}

@Composable
fun TestItem(e: Event, today: LocalDate) {
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, LocalDate.parse(e.date))
    val c = MaterialTheme.colorScheme
    val (bg, fg) = if (days <= 1) c.errorContainer to c.onErrorContainer else c.tertiaryContainer to c.onTertiaryContainer
    Item(
        e.subject.ifBlank { e.author }, longDate(e.date), e.notes, expandable = true,
        leading = { IconCircle(Icons.Rounded.Quiz, c.surfaceContainerHighest, c.onSurfaceVariant) },
        trailing = {
            Text(
                when (days) { 0L -> "Oggi"; 1L -> "Domani"; else -> "Tra $days giorni" },
                Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
                color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold
            )
        }
    )
}

@Composable
fun ReminderDialog(day: LocalDate, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.EditNote, null) },
        title = { Text("Promemoria") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(longDate(day.toString()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), placeholder = { Text("Es. portare il libro di disegno") }, minLines = 2)
                Text("Resta solo su questo telefono.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton({ onSave(text) }, enabled = text.isNotBlank()) { Text("Salva") } },
        dismissButton = { TextButton(onDismiss) { Text("Annulla") } }
    )
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
    val kinds = listOf("Assenze" to "Assenza", "Ritardi" to "Ritardo", "Assenze parziali" to "Assenza parziale", "Uscite anticipate" to "Uscita anticipata")
    Screen {
        if (list.isEmpty()) item {
            Card(
                Modifier.fillMaxWidth().appear(0),
                colors = CardDefaults.cardColors(containerColor = averageCardColors(10.0).first, contentColor = averageCardColors(10.0).second)
            ) {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Verified, null, Modifier.size(48.dp))
                    Text("Presenza perfetta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Quest'anno nessuna assenza, ritardo o uscita anticipata.", textAlign = TextAlign.Center)
                }
            }
        }
        if (list.isNotEmpty()) kinds.chunked(2).forEachIndexed { row, pair ->
            item {
                Row(Modifier.appear(row), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { (label, type) ->
                        val (_, bg, fg) = absenceStyle(type)
                        val of = list.filter { it.type == type }
                        val n = animatedValue(of.size.toDouble())
                        val open = of.count { !it.justified }
                        Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg)) {
                            Column(Modifier.padding(16.dp)) {
                                Text("${Math.round(n)}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                Text(
                                    when {
                                        of.isEmpty() -> "Nessuna"
                                        open == 0 -> if (of.size == 1) "Giustificata" else "Tutte giustificate"
                                        else -> "$open da giustificare"
                                    },
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
        var index = 3
        list.groupBy { it.date.take(7) }.forEach { (month, events) ->
            val headerIndex = index
            item { Box(Modifier.appear(headerIndex)) { Header(monthName(month)) } }
            animItems(events, month, index + 1) {
                val (letter, bg, fg) = absenceStyle(it.type)
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

/** Tutto quello che riguarda un giorno: assenze, voti, compiti ed eventi, lezioni. */
@Composable
fun DayContent(
    day: LocalDate, today: LocalDate, lessons: List<Lesson>, agenda: List<Event>, grades: List<Grade>, absences: List<Absence>,
    reminders: List<Reminder> = emptyList(), onAddReminder: (() -> Unit)? = null, onRemoveReminder: (Reminder) -> Unit = {}
) {
    val d = day.toString()
    val dayAbsences = absences.filter { it.date.take(10) == d }
    val dayGrades = grades.filter { it.date.take(10) == d }
    val dayEvents = agenda.filter { it.date == d }
    val dayLessons = lessons.filter { it.date == d }
    val small: @Composable (String) -> Unit = {
        Text(it, Modifier.padding(start = 4.dp, top = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DayLabel(day)
        if (dayAbsences.isEmpty() && dayGrades.isEmpty() && dayEvents.isEmpty() && dayLessons.isEmpty() && reminders.isEmpty()) {
            Empty(if (day > today) "Niente in programma" else "Nessuna lezione registrata")
        }
        if (dayLessons.isNotEmpty()) {
            small("Lezioni")
            dayLessons.forEach { LessonItem(it) }
        }
        if (dayEvents.isNotEmpty()) {
            small(if (dayEvents.all { it.homework }) "Compiti" else "Compiti ed eventi")
            dayEvents.forEach { EventItem(it) }
        }
        if (reminders.isNotEmpty()) {
            small("I tuoi promemoria")
            reminders.forEach { r ->
                Item(
                    r.text, leading = { IconCircle(Icons.Rounded.EditNote, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) },
                    trailing = { IconButton({ onRemoveReminder(r) }) { Icon(Icons.Rounded.Close, "Elimina promemoria") } }
                )
            }
        }
        if (dayGrades.isNotEmpty()) {
            small("Voti")
            dayGrades.forEach { g -> Item(g.subject, g.type, g.notes, expandable = true, leading = { GradeCircle(g) }) }
        }
        if (dayAbsences.isNotEmpty()) {
            small("Presenze")
            dayAbsences.forEach {
                val (letter, bg, fg) = absenceStyle(it.type)
                Item(it.type, null, if (it.justified) "Giustificata" else "Da giustificare", leading = { Circle(letter, bg, fg) })
            }
        }
        onAddReminder?.let { add ->
            TextButton(add, Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Aggiungi promemoria")
            }
        }
    }
}

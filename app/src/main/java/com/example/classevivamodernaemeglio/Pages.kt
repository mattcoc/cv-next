package com.example.classevivamodernaemeglio

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CoPresent
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

sealed interface Page {
    data class NoticePage(val notice: Notice) : Page
    data class FilePage(val title: String, val file: File) : Page
    data class Web(val title: String, val url: String) : Page
    data object Absences : Page
    data object Notes : Page
    data object Didactics : Page
    data object Documents : Page
    data object Lessons : Page
}

const val WEB = "https://web.spaggiari.eu"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubScreen(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro") } },
                actions = actions
            )
        }
    ) { p -> Box(Modifier.fillMaxSize().padding(top = p.calculateTopPadding())) { content() } }
}

private class Fn(val label: String, val icon: ImageVector, val web: Boolean = false, val action: () -> Unit)

@Composable
fun FunctionsScreen(open: (Page) -> Unit, onHomework: () -> Unit, goto: (Section) -> Unit) {
    val school = listOf(
        Fn("Valutazioni", Icons.Rounded.Grade) { goto(Section.Voti) },
        Fn("Bacheca", Icons.Rounded.Campaign) { goto(Section.Bacheca) },
        Fn("Scrutini", Icons.Rounded.Insights) { open(Page.Documents) },
        Fn("Assenze", Icons.Rounded.EventBusy) { open(Page.Absences) },
        Fn("Note", Icons.Rounded.Feedback) { open(Page.Notes) },
        Fn("Agenda", Icons.Rounded.CalendarMonth) { goto(Section.Agenda) },
        Fn("Lezioni", Icons.Rounded.CoPresent) { open(Page.Lessons) },
        Fn("File dei docenti", Icons.Rounded.FolderShared) { open(Page.Didactics) },
        Fn("Compiti", Icons.AutoMirrored.Rounded.Assignment) { onHomework() }
    )
    val web = listOf(
        Fn("Colloqui", Icons.Rounded.Groups, true) { open(Page.Web("Colloqui", WebLinks.meetings)) },
        Fn("Sportello", Icons.Rounded.SupportAgent, true) { open(Page.Web("Sportello", WebLinks.desk)) },
        Fn("PagOnline", Icons.Rounded.CreditCard, true) { open(Page.Web("PagOnline", WebLinks.payments)) },
        Fn("Progettiamo la Scuola", Icons.AutoMirrored.Rounded.MenuBook, true) { open(Page.Web("Progettiamo la Scuola", WebLinks.projects)) },
        Fn("Certificazioni", Icons.Rounded.WorkspacePremium, true) { open(Page.Web("Certificazioni", WebLinks.certificates)) },
        Fn("Registro Web", Icons.Rounded.Language, true) { open(Page.Web("Registro Web", WebLinks.home)) },
        Fn("Anno precedente", Icons.Rounded.History, true) { open(Page.Web("Anno precedente", WebLinks.lastYear)) }
    )
    Screen {
        item { Box(Modifier.appear(0)) { Header("Scuola") } }
        grid(school, 1)
        item { Box(Modifier.appear(5)) { Header("Servizi Web") } }
        grid(web, 6)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.grid(fns: List<Fn>, start: Int) =
    fns.chunked(3).forEachIndexed { row, chunk ->
        item {
            Row(Modifier.appear(start + row), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                chunk.forEach { FnCard(it, Modifier.weight(1f)) }
                repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }

@Composable
private fun FnCard(fn: Fn, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Card(fn.action, modifier.height(116.dp)) {
        Box(Modifier.fillMaxSize()) {
            if (fn.web) Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.align(Alignment.TopEnd).padding(10.dp).size(14.dp), tint = c.onSurfaceVariant)
            Column(
                Modifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
            ) {
                IconCircle(fn.icon, if (fn.web) c.tertiaryContainer else c.primaryContainer, if (fn.web) c.onTertiaryContainer else c.onPrimaryContainer)
                Text(fn.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun NotesScreen(list: List<NoteItem>) {
    val c = MaterialTheme.colorScheme
    Screen {
        if (list.isEmpty()) item { Empty("Nessuna nota. Ottimo così!") }
        animItems(list, "notes") {
            val (bg, fg) = when (it.type) {
                "Nota disciplinare", "Sanzione disciplinare" -> c.errorContainer to c.onErrorContainer
                "Richiamo" -> c.tertiaryContainer to c.onTertiaryContainer
                else -> c.secondaryContainer to c.onSecondaryContainer
            }
            Item(it.type, listOf(longDate(it.date), it.author).filter(String::isNotBlank).joinToString(" · "), it.text,
                bold = !it.read, expandable = true, leading = { IconCircle(Icons.Rounded.Feedback, bg, fg) })
        }
    }
}

@Composable
fun LessonsScreen(lessons: List<Lesson>) {
    val today = remember { LocalDate.now() }
    val days = remember { (30L downTo 0L).map { today.minusDays(it) }.filter { it.dayOfWeek != DayOfWeek.SUNDAY } }
    val byDay = remember(lessons) { lessons.groupBy { it.date } }
    var selected by rememberSaveable { mutableStateOf(if (today.toString() in byDay) today.toString() else byDay.keys.maxOrNull() ?: today.toString()) }
    Screen {
        item { Box(Modifier.appear(0)) { DayStrip(days, LocalDate.parse(selected), { byDay.containsKey(it.toString()) }) { selected = it.toString() } } }
        item {
            Box(Modifier.appear(1)) {
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
    }
}

@Composable
fun DidacticsScreen(api: Api, teachers: List<DidTeacher>, open: (Page) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf<Long?>(null) }
    var text by remember { mutableStateOf<String?>(null) }
    val c = MaterialTheme.colorScheme
    Screen {
        if (teachers.isEmpty()) item { Empty("I docenti non hanno ancora condiviso file") }
        animItems(teachers, "teachers") { t ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(Modifier.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(t.name, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(t.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(if (t.folders.size == 1) "1 cartella" else "${t.folders.size} cartelle", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                        }
                    }
                    t.folders.forEach { f ->
                        var expanded by rememberSaveable(t.name + f.name) { mutableStateOf(false) }
                        val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "r")
                        Row(
                            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp, 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Folder, null, tint = c.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.name.ifBlank { "Senza nome" }, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${f.contents.size} elementi · ${shortDate(f.date)}", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                            }
                            Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.rotate(rotation), tint = c.onSurfaceVariant)
                        }
                        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            Column(Modifier.padding(start = 28.dp, end = 8.dp)) {
                                f.contents.forEach { item ->
                                    Row(
                                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(enabled = loading == null) {
                                            loading = item.id
                                            scope.launch {
                                                runCatching { api.didacticItem(context.cacheDir, item) }
                                                    .onSuccess {
                                                        when (it) {
                                                            is Opened.Pdf -> open(Page.FilePage(item.name, it.file))
                                                            is Opened.Other -> openFile(context, it.file)
                                                            is Opened.Link -> openLink(context, it.url)
                                                            is Opened.Text -> text = it.text
                                                        }
                                                    }
                                                    .onFailure { Toast.makeText(context, "Impossibile aprire il file", Toast.LENGTH_SHORT).show() }
                                                loading = null
                                            }
                                        }.padding(12.dp, 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val icon = when {
                                            item.type == "link" -> Icons.Rounded.Link
                                            item.type == "text" -> Icons.Rounded.Notes
                                            item.name.endsWith(".pdf", true) -> Icons.Rounded.PictureAsPdf
                                            else -> Icons.Rounded.Description
                                        }
                                        if (loading == item.id) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                        else Icon(icon, null, Modifier.size(22.dp), tint = c.onSurfaceVariant)
                                        Spacer(Modifier.width(12.dp))
                                        Text(item.name.ifBlank { "Contenuto" }, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    text?.let { t -> AlertDialog({ text = null }, { TextButton({ text = null }) { Text("Chiudi") } }, text = { Text(t) }) }
}

@Composable
fun DocumentsScreen(api: Api, docs: Documents, open: (Page) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf<String?>(null) }
    val c = MaterialTheme.colorScheme
    Screen {
        item {
            Box(Modifier.appear(0)) {
                Item("Esito scrutini", "Dal sito web del registro", "Primo e secondo periodo, con voti ed esiti", onClick = { open(Page.Web("Esito scrutini", WebLinks.reports)) },
                    leading = { IconCircle(Icons.Rounded.Insights, c.tertiaryContainer, c.onTertiaryContainer) },
                    trailing = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = c.onSurfaceVariant) })
            }
        }
        item { Box(Modifier.appear(1)) { Header("Pagelle") } }
        if (docs.reports.isEmpty()) item { Empty("Nessuna pagella disponibile") }
        animItems(docs.reports, "reports", 1) {
            Item(it.title, "Si apre dal sito", onClick = { open(Page.Web(it.title, it.url)) },
                leading = { IconCircle(Icons.Rounded.Insights, c.primaryContainer, c.onPrimaryContainer) },
                trailing = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = c.onSurfaceVariant) })
        }
        item { Box(Modifier.appear(2)) { Header("Documenti") } }
        if (docs.docs.isEmpty()) item { Empty("Nessun documento") }
        animItems(docs.docs, "docs", 3) { d ->
            Item(d.title, onClick = {
                if (loading == null) {
                    loading = d.hash
                    scope.launch {
                        runCatching { api.document(context.cacheDir, d) }
                            .onSuccess { open(Page.FilePage(d.title, it)) }
                            .onFailure { Toast.makeText(context, it.message ?: "Documento non disponibile", Toast.LENGTH_SHORT).show() }
                        loading = null
                    }
                }
            }, leading = {
                if (loading == d.hash) Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                else IconCircle(Icons.Rounded.PictureAsPdf, c.secondaryContainer, c.onSecondaryContainer)
            })
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(api: Api, title: String, url: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var canBack by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { api.webSession() }
        ready = true
    }
    SubScreen(title, onBack, actions = {
        IconButton({ web?.reload() }) { Icon(Icons.Rounded.Refresh, "Ricarica") }
        IconButton({ openLink(context, web?.url ?: url) }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Apri nel browser") }
    }) {
        BackHandler(canBack) { web?.goBack() }
        if (!ready) LoadingState()
        else AndroidView(
            factory = {
                WebView(it).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress / 100f }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) { canBack = view.canGoBack() }
                    }
                    setDownloadListener { link, _, _, mime, _ -> openLink(context, link) }
                    loadUrl(url)
                    web = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        if (ready && progress < 1f) LinearProgressIndicator({ progress }, Modifier.fillMaxWidth())
    }
}

fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Nessuna app per aprire il link", Toast.LENGTH_SHORT).show()
    }
}

fun openFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "*/*"
    try {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Apri con"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Nessuna app per aprire il file", Toast.LENGTH_SHORT).show()
    }
}

object WebLinks {
    const val home = "$WEB/home/app/default/menu_webinfoschool_studenti.php"
    const val meetings = "$WEB/fml/app/default/genitori_colloqui.php"
    const val desk = "$WEB/fml/app/default/alunni_sportello.php"
    const val certificates = "$WEB/cvv/app/default/att_cert_studente.php"
    const val payments = "$WEB/pfo/app/default/scadenze.php"
    const val reports = "$WEB/sol/app/default/documenti_sol.php?quad=1"
    const val projects = "$WEB/pdp/app/default/lista_pfi_studente.php"
    const val lastYear = "$WEB/home/app/default/xasapi.php?a=lap&bu=https://web25.spaggiari.eu&ru=/home/&fu=xasapi-ERROR.php"
}

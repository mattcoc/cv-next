package io.github.mattcoc.cvnext

import android.graphics.Bitmap
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.IconButton
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

class PdfDoc(val file: File) : Closeable {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    private val mutex = Mutex()
    val count = renderer.pageCount
    @Volatile private var closed = false
    private var released = false

    // Chiamata sempre con il lucchetto preso: il renderer non si chiude mentre disegna una pagina.
    private fun release() {
        if (released) return
        released = true
        runCatching { renderer.close() }
        runCatching { fd.close() }
    }

    suspend fun render(index: Int, width: Int): Bitmap = mutex.withLock {
        if (closed) throw kotlinx.coroutines.CancellationException("PDF chiuso")
        try {
            withContext(Dispatchers.IO) {
                val safeWidth = width.coerceAtLeast(100)
                val page = renderer.openPage(index)
                try {
                    // Pagine lunghissime (scansioni): altezza limitata per non esaurire la memoria
                    val targetHeight = (safeWidth * page.height / page.width.coerceAtLeast(1)).coerceIn(100, 8_000_000 / safeWidth)
                    Bitmap.createBitmap(safeWidth, targetHeight, Bitmap.Config.ARGB_8888).apply {
                        eraseColor(android.graphics.Color.WHITE)
                        page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                } finally {
                    page.close()
                }
            }
        } finally {
            if (closed) release()
        }
    }

    /** Se una pagina è in disegno, la chiusura avviene appena finisce. */
    override fun close() {
        closed = true
        if (mutex.tryLock()) try { release() } finally { mutex.unlock() }
    }
}

@Composable
fun rememberPdf(key: Any?, load: (suspend () -> File)?): Result<PdfDoc>? {
    val doc by produceState<Result<PdfDoc>?>(null, key) {
        value = null
        if (load == null) return@produceState
        val d = runCatching { PdfDoc(load()) }
        value = d
        awaitDispose { d.getOrNull()?.close() }
    }
    return doc
}

@Composable
fun PdfViewer(
    doc: Result<PdfDoc>?,
    headerItems: Int,
    title: String,
    prefix: String = "",
    empty: String? = null,
    header: LazyListScope.() -> Unit = {},
    footer: LazyListScope.() -> Unit = {}
) {
    val list = rememberLazyListState()
    var full by remember { mutableStateOf<Int?>(null) }
    val page by remember { derivedStateOf { (list.firstVisibleItemIndex - headerItems).coerceAtLeast(1) } }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val px = with(LocalDensity.current) { (maxWidth - 32.dp).toPx() }
        val renderWidth = (px * 1.25f).toInt().coerceAtMost(1400)
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, bottom + 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            header()
            when {
                empty != null -> item { Empty(empty) }
                doc == null -> item { Loading("Apertura del PDF…") }
                doc.isFailure -> item { Empty(doc.exceptionOrNull()?.message ?: "Impossibile aprire il PDF") }
                else -> {
                    val d = doc.getOrThrow()
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.TouchApp, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Text("Tocca una pagina per leggerla a schermo intero", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(d.count) { i -> PdfPage(d, i, renderWidth, Modifier.appear(i + 1)) { full = i } }
                    footer()
                }
            }
        }
        val pages = doc?.getOrNull()?.count ?: 0
        AnimatedVisibility(
            pages > 0 && full == null,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, shadowElevation = 8.dp) {
                Text(
                    "${prefix}Pagina ${page.coerceAtMost(pages)} di $pages",
                    Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
    val d = doc?.getOrNull()
    if (d != null) full?.let { FullPdf(d, it, title) { full = null } }
}

@Composable
fun PdfActions(doc: Result<PdfDoc>?, title: String) {
    val context = LocalContext.current
    val file = doc?.getOrNull()?.file
    IconButton({ file?.let { savePdf(context, it, title) } }, enabled = file != null) { Icon(Icons.Rounded.Download, "Scarica") }
    IconButton({ file?.let { sharePdf(context, it, title) } }, enabled = file != null) { Icon(Icons.Rounded.Share, "Condividi") }
}

@Composable
fun FullPdf(doc: PdfDoc, start: Int, title: String, onClose: () -> Unit) {
    val pager = rememberPagerState(start) { doc.count }
    var chrome by remember { mutableStateOf(true) }
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF111111))) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val width = with(LocalDensity.current) { (maxWidth.toPx() * 2).toInt().coerceAtMost(2200) }
                HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { i ->
                    ZoomPage(doc, i, width) { chrome = !chrome }
                }
            }
            AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                            .statusBarsPadding().padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClose) { Icon(Icons.Rounded.Close, "Chiudi", tint = Color.White) }
                        Text(title, Modifier.weight(1f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        val context = LocalContext.current
                        IconButton({ savePdf(context, doc.file, title) }) { Icon(Icons.Rounded.Download, "Scarica", tint = Color.White) }
                        IconButton({ sharePdf(context, doc.file, title) }) { Icon(Icons.Rounded.Share, "Condividi", tint = Color.White) }
                    }
                    Surface(
                        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
                        shape = CircleShape, color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White
                    ) {
                        Text("${pager.currentPage + 1} / ${doc.count}", Modifier.padding(horizontal = 18.dp, vertical = 10.dp), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun ZoomPage(doc: PdfDoc, index: Int, width: Int, onTap: () -> Unit) {
    val bitmap by produceState<Bitmap?>(null, doc, index, width) { value = runCatching { doc.render(index, width) }.getOrNull() }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val shown by animateFloatAsState(scale, spring(0.9f, 600f), label = "z")
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        fun clamp(o: Offset, s: Float) = Offset(o.x.coerceIn(-(s - 1) * w / 2, (s - 1) * w / 2), o.y.coerceIn(-(s - 1) * h / 2, (s - 1) * h / 2))
        val b = bitmap
        if (b == null) CircularProgressIndicator(color = Color.White)
        else Image(
            b.asImageBitmap(), "Pagina ${index + 1}",
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.size > 1 || scale > 1f) {
                                scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                                offset = clamp(offset + event.calculatePan(), scale)
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { p ->
                            if (scale > 1f) { scale = 1f; offset = Offset.Zero }
                            else { scale = 2.5f; offset = clamp(Offset((w / 2 - p.x) * 1.5f, (h / 2 - p.y) * 1.5f), 2.5f) }
                        }
                    )
                }
                .graphicsLayer {
                    scaleX = shown
                    scaleY = shown
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

fun savePdf(context: Context, file: File, title: String) {
    val name = title.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().take(80).ifBlank { "documento" } + ".pdf"
    val ok = runCatching {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
            resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            true
        } else false
    }.getOrDefault(false)
    if (ok) Toast.makeText(context, "Salvato in Download: $name", Toast.LENGTH_SHORT).show() else sharePdf(context, file, title)
}

fun sharePdf(context: Context, file: File, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Condividi"))
}

@Composable
fun NoticeScreen(api: Api, notice: Notice, onClose: () -> Unit) {
    val context = LocalContext.current
    var attachment by remember { mutableStateOf(notice.attachments.firstOrNull()) }
    val text by produceState<String?>(null) { value = runCatching { api.readNotice(notice) }.getOrDefault("") }
    val a = attachment
    val doc = rememberPdf(a, if (a == null) null else ({ api.attachment(context.cacheDir, notice, a) }))
    val many = notice.attachments.size > 1
    SubScreen(notice.title, onClose, actions = { PdfActions(doc, a?.name?.removeSuffix(".pdf") ?: notice.title) }) {
        PdfViewer(
            doc,
            headerItems = if (many) 3 else 1,
            title = a?.name?.removeSuffix(".pdf") ?: notice.title,
            prefix = if (many) "Allegato ${notice.attachments.indexOf(attachment) + 1}/${notice.attachments.size} · " else "",
            empty = if (a == null) (if (text == null) "Caricamento…" else "Nessun allegato") else null,
            header = {
                item {
                    Card(Modifier.fillMaxWidth().appear(0)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(listOf(longDate(notice.date), notice.category).filter(String::isNotBlank).joinToString(" · "),
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(notice.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            text?.takeIf { it.isNotBlank() }?.let { ExpandableText(it, lines = 6) }
                        }
                    }
                }
                if (many) {
                    item { Header("${notice.attachments.size} allegati da leggere") }
                    item {
                        LazyRow(Modifier.fadeEdges(), contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(notice.attachments) { i, att ->
                                AttachmentCard(att, i, notice.attachments.size, att == attachment, Modifier.appear(i)) { attachment = att }
                            }
                        }
                    }
                }
            },
            footer = {
                val next = notice.attachments.getOrNull(notice.attachments.indexOf(attachment) + 1)
                if (next != null) item {
                    FilledTonalButton({ attachment = next }, Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Apri l'allegato successivo", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
                    }
                }
            }
        )
    }
}

@Composable
fun FileScreen(title: String, file: File, onClose: () -> Unit) {
    val doc = rememberPdf(file) { file }
    SubScreen(title, onClose, actions = { PdfActions(doc, title) }) { PdfViewer(doc, headerItems = 0, title = title) }
}

@Composable
fun Loading(text: String) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator()
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AttachmentCard(a: Attachment, index: Int, total: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Card(
        onClick, modifier.width(200.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) c.primaryContainer else c.surfaceContainerHigh),
        border = if (selected) BorderStroke(2.dp, c.primary) else null
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PictureAsPdf, null, tint = if (selected) c.onPrimaryContainer else c.primary)
                Spacer(Modifier.width(8.dp))
                Text("Allegato ${index + 1} di $total", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(a.name.removeSuffix(".pdf").removeSuffix(".PDF"), style = MaterialTheme.typography.bodyMedium, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun PdfPage(doc: PdfDoc, index: Int, width: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bitmap by produceState<Bitmap?>(null, doc, index, width) { value = runCatching { doc.render(index, width) }.getOrNull() }
    Card(
        onClick,
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        val b = bitmap
        if (b == null) Box(Modifier.fillMaxWidth().aspectRatio(1 / 1.414f), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(32.dp))
        } else Image(b.asImageBitmap(), "Pagina ${index + 1}", Modifier.fillMaxWidth().aspectRatio(b.width.toFloat() / b.height))
    }
}

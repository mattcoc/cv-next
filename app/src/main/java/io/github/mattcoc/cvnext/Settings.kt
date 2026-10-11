package io.github.mattcoc.cvnext

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var seed by mutableIntStateOf(prefs.getInt("seed", if (Build.VERSION.SDK_INT >= 31) 0 else seeds[1].first))
        private set
    var mode by mutableIntStateOf(prefs.getInt("mode", 0))
        private set
    var alerts by mutableStateOf(read("alerts").orEmpty())
        private set
    var autoUpdate by mutableStateOf(prefs.getBoolean("autoUpdate", true))
        private set
    var evening by mutableStateOf(prefs.getBoolean("evening", false))
        private set
    var eveningHour by mutableIntStateOf(prefs.getInt("eveningHour", 19))
        private set

    fun updateEvening(on: Boolean = evening, hour: Int = eveningHour) {
        evening = on
        eveningHour = hour
        prefs.edit().putBoolean("evening", on).putInt("eveningHour", hour).apply()
    }

    private fun read(key: String) = runCatching {
        prefs.getStringSet(key, null)?.mapNotNull { n -> Alert.entries.firstOrNull { it.name == n } }?.toSet()
    }.getOrNull()

    private fun save(set: Set<Alert>) {
        alerts = set
        prefs.edit().putStringSet("alerts", set.map { it.name }.toSet()).apply()
    }

    fun toggle(alert: Alert, on: Boolean) = save(if (on) alerts + alert else alerts - alert)

    /** Interruttore generale: spegnendo ricorda la scelta, riaccendendo la ripristina. */
    fun toggleAll(on: Boolean) {
        if (on) save(read("alertsBefore")?.takeIf { it.isNotEmpty() } ?: Alert.entries.toSet())
        else {
            prefs.edit().putStringSet("alertsBefore", alerts.map { it.name }.toSet()).apply()
            save(emptySet())
        }
    }

    fun update(seed: Int = this.seed, mode: Int = this.mode) {
        this.seed = seed
        this.mode = mode
        prefs.edit().putInt("seed", seed).putInt("mode", mode).apply()
    }

    fun updateAuto(on: Boolean) {
        autoUpdate = on
        prefs.edit().putBoolean("autoUpdate", on).apply()
    }
}

val seeds = listOf(
    0 to "Sfondo",
    0xFF3F51B5.toInt() to "Indaco",
    0xFF1E88E5.toInt() to "Blu",
    0xFF00897B.toInt() to "Turchese",
    0xFF43A047.toInt() to "Verde",
    0xFFF9A825.toInt() to "Giallo",
    0xFFF4511E.toInt() to "Arancio",
    0xFFE53935.toInt() to "Rosso",
    0xFFD81B60.toInt() to "Rosa",
    0xFF8E24AA.toInt() to "Viola"
)

private val Alert.icon: ImageVector
    get() = when (this) {
        Alert.Notices -> Icons.Rounded.Campaign
        Alert.Grades -> Icons.Rounded.Grade
        Alert.Homework -> Icons.Rounded.CalendarMonth
        Alert.Didactics -> Icons.Rounded.Folder
        Alert.Notes -> Icons.Rounded.Feedback
        Alert.Payments -> Icons.Rounded.CreditCard
        Alert.Mentions -> Icons.Rounded.RecordVoiceOver
    }

/** Spazio occupato dai dati dell'app, per la sezione "Dati e spazio". */
object Storage {
    private fun offlineDir(context: Context) = File(context.noBackupFilesDir, "api")
    private fun downloads(context: Context) = context.cacheDir.listFiles().orEmpty()
        .filter { it.name.startsWith("notice_") || it.name.startsWith("did_") || it.name.startsWith("doc_") || it.name == "updates" }

    private fun File.size(): Long = if (isDirectory) listFiles().orEmpty().sumOf { it.size() } else length()

    fun offlineSize(context: Context) = offlineDir(context).size()
    fun downloadsSize(context: Context) = downloads(context).sumOf { it.size() }
    fun clearOffline(context: Context) = offlineDir(context).deleteRecursively()
    fun clearDownloads(context: Context) = downloads(context).forEach { it.deleteRecursively() }
}

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Ready(val release: Release, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}


@Composable
fun SettingsScreen(settings: Settings, api: Api? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    var privacy by remember { mutableStateOf(false) }
    SubScreen("Impostazioni", onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Box(Modifier.appear(0)) { SectionTitle("Aspetto") } }
            item { Box(Modifier.appear(1)) { AppearanceCard(settings) } }
            item { Box(Modifier.appear(2)) { SectionTitle("Notifiche") } }
            item { Box(Modifier.appear(3)) { NotificationsCard(settings, api) } }
            item { Box(Modifier.appear(4)) { SectionTitle("Aggiornamenti") } }
            item { Box(Modifier.appear(5)) { UpdatesCard(settings) } }
            item { Box(Modifier.appear(6)) { SectionTitle("Dati e spazio") } }
            item { Box(Modifier.appear(7)) { StorageCard() } }
            item { Box(Modifier.appear(8)) { SectionTitle("Informazioni") } }
            item {
                Box(Modifier.appear(9)) {
                    Group {
                        SettingRow(Icons.Rounded.Info, "CV Next ${Updater.installed(context)}", "App non ufficiale, non affiliata a Gruppo Spaggiari Parma", onClick = { openLink(context, "$REPO/releases") }, trailing = { Chevron() })
                        RowDivider()
                        SettingRow(Icons.Rounded.Code, "Codice sorgente", "github.com/mattcoc/cv-next", onClick = { openLink(context, REPO) }, trailing = { Chevron() })
                        RowDivider()
                        SettingRow(Icons.Rounded.BugReport, "Segnala un problema", "Apre una segnalazione su GitHub", onClick = { openLink(context, issueUrl(context)) }, trailing = { Chevron() })
                        RowDivider()
                        SettingRow(Icons.Rounded.PrivacyTip, "Privacy", "Cosa salva l'app e con chi comunica", onClick = { privacy = true }, trailing = { Chevron() })
                    }
                }
            }
        }
    }
    if (privacy) AlertDialog(
        onDismissRequest = { privacy = false },
        confirmButton = { TextButton({ privacy = false }) { Text("Chiudi") } },
        icon = { Icon(Icons.Rounded.PrivacyTip, null) },
        title = { Text("Privacy") },
        text = {
            Text(
                "• Le credenziali restano solo su questo telefono, cifrate con l'Android Keystore.\n" +
                    "• L'app comunica con i server di Gruppo Spaggiari e, solo per gli aggiornamenti, con GitHub.\n" +
                    "• Nessun server dello sviluppatore, nessuna statistica, nessuna pubblicità.\n" +
                    "• I dati salvati per l'uso offline restano nella memoria privata dell'app e non finiscono nei backup.\n" +
                    "• Uscendo da un account vengono cancellati le sue credenziali e i suoi dati."
            )
        }
    )
}


@Composable
private fun SectionTitle(text: String) = Text(
    text, Modifier.padding(start = 4.dp, top = 16.dp, bottom = 2.dp),
    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary
)

@Composable
private fun Group(content: @Composable () -> Unit) = Column(
    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerLow).animateContentSize()
) { content() }

@Composable
private fun RowDivider() = HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh)

@Composable
private fun Chevron() = Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(16.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (accent) c.primaryContainer else c.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, Modifier.size(22.dp), tint = if (accent) c.onPrimaryContainer else c.onSurfaceVariant) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant) }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun CheckSwitch(on: Boolean, onChange: (Boolean) -> Unit) = Switch(
    on, onChange,
    thumbContent = if (on) { { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) } } else null
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceCard(settings: Settings) {
    val c = MaterialTheme.colorScheme
    Group {
        Text("Tema", Modifier.padding(16.dp, 16.dp, 16.dp, 10.dp), style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Sistema", "Chiaro", "Scuro").forEachIndexed { i, label ->
                val sel = settings.mode == i
                Column(
                    Modifier.weight(1f).clip(MaterialTheme.shapes.medium).selectable(sel, role = Role.RadioButton) { settings.update(mode = i) }.padding(bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(84.dp).clip(MaterialTheme.shapes.medium)
                            .border(if (sel) 2.dp else 1.dp, if (sel) c.primary else c.outlineVariant, MaterialTheme.shapes.medium)
                    ) {
                        when (i) {
                            0 -> { ThemeMini(false, Modifier.weight(1f)); ThemeMini(true, Modifier.weight(1f)) }
                            1 -> ThemeMini(false, Modifier.weight(1f))
                            else -> ThemeMini(true, Modifier.weight(1f))
                        }
                    }
                    Text(
                        label, style = MaterialTheme.typography.labelLarge,
                        color = if (sel) c.primary else c.onSurfaceVariant, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
        HorizontalDivider(Modifier.padding(16.dp, 12.dp), color = c.surfaceContainerHigh)
        Text("Colore", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge)
        if (Build.VERSION.SDK_INT >= 31) FilterChip(
            settings.seed == 0, { settings.update(seed = 0) }, { Text("Dallo sfondo") },
            Modifier.padding(horizontal = 16.dp),
            leadingIcon = { Icon(if (settings.seed == 0) Icons.Rounded.Check else Icons.Rounded.Wallpaper, null, Modifier.size(18.dp)) }
        )
        // Cerchi da 44 dp (area minima da toccare) che vanno a capo sugli schermi stretti.
        FlowRow(
            Modifier.fillMaxWidth().padding(16.dp, 8.dp, 16.dp, 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            seeds.filter { it.first != 0 }.forEach { (seed, label) ->
                val sel = settings.seed == seed
                Box(
                    Modifier.size(44.dp)
                        .then(if (sel) Modifier.border(2.dp, Color(seed), CircleShape).padding(4.dp) else Modifier)
                        .clip(CircleShape).background(Color(seed))
                        .selectable(sel, role = Role.RadioButton) { settings.update(seed = seed) }
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center
                ) { if (sel) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

@Composable
private fun ThemeMini(dark: Boolean, modifier: Modifier) {
    val bg = if (dark) Color(0xFF1B1B21) else Color(0xFFFBF8FF)
    val accent = if (dark) Color(0xFFBAC3FF) else Color(0xFF3949AB)
    val card = if (dark) Color(0xFF2E2F42) else Color(0xFFDDE1FF)
    Column(modifier.fillMaxSize().background(bg).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth(0.6f).height(8.dp).clip(CircleShape).background(accent))
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp)).background(card))
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp)).background(card))
    }
}

@Composable
private fun NotificationsCard(settings: Settings, api: Api?) {
    var editingNames by remember { mutableStateOf(false) }
    val account = api?.let { a -> a.accounts.firstOrNull { it.key == a.current } }
    if (editingNames && account != null) NamesDialog(account) { editingNames = false }
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pending?.invoke()
        pending = null
        Notifier.schedule(context, settings.alerts)
    }
    val withPermission: (() -> Unit) -> Unit = { action ->
        val needs = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needs) {
            pending = action
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
            Notifier.schedule(context, settings.alerts)
        }
    }
    val any = settings.alerts.isNotEmpty()
    val last = remember { context.getSharedPreferences("seen", Context.MODE_PRIVATE).getLong("lastCheck", 0) }
    val status = when {
        !any -> "Disattivate"
        last == 0L -> "Controllo circa ogni ora"
        else -> "Controllo circa ogni ora · ultimo " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(last)).let { t ->
            if (System.currentTimeMillis() - last < 20 * 3600 * 1000L) "alle $t" else "il " + DateFormat.getDateInstance(DateFormat.SHORT).format(Date(last))
        }
    }
    Group {
        SettingRow(
            if (any) Icons.Rounded.NotificationsActive else Icons.Rounded.Notifications, "Notifiche", status, accent = true,
            onClick = { if (any) { settings.toggleAll(false); Notifier.schedule(context, settings.alerts) } else withPermission { settings.toggleAll(true) } },
            trailing = {
                CheckSwitch(any) { on ->
                    if (on) withPermission { settings.toggleAll(true) } else { settings.toggleAll(false); Notifier.schedule(context, settings.alerts) }
                }
            }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
        Alert.entries.forEachIndexed { i, alert ->
            val on = alert in settings.alerts
            val change: (Boolean) -> Unit = { v ->
                if (v) withPermission { settings.toggle(alert, true) } else { settings.toggle(alert, false); Notifier.schedule(context, settings.alerts) }
            }
            SettingRow(alert.icon, alert.title, alert.description, onClick = { change(!on) }, trailing = { CheckSwitch(on, change) })
            if (alert == Alert.Mentions && on && account != null) {
                val names = Mentions.names(context, account)
                SettingRow(
                    Icons.Rounded.Badge, "Nomi da cercare", names.joinToString(", ").ifBlank { "Nessuno" },
                    onClick = { editingNames = true }, trailing = { Chevron() }
                )
            }
            if (i < Alert.entries.lastIndex) RowDivider()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
        val setEvening: (Boolean) -> Unit = { on ->
            if (on) withPermission { settings.updateEvening(on = true); Evening.schedule(context, settings) }
            else { settings.updateEvening(on = false); Evening.schedule(context, settings) }
        }
        SettingRow(
            Icons.Rounded.Bedtime, "Promemoria della sera",
            if (settings.evening) "Alle ${settings.eveningHour}:00 compiti, verifiche e promemoria del giorno dopo" else "Compiti, verifiche e promemoria del giorno dopo",
            onClick = { setEvening(!settings.evening) },
            trailing = { CheckSwitch(settings.evening, setEvening) }
        )
        if (settings.evening) Row(Modifier.padding(72.dp, 0.dp, 16.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(18, 19, 20, 21).forEach { h ->
                FilterChip(settings.eveningHour == h, { settings.updateEvening(hour = h); Evening.schedule(context, settings) }, { Text("$h:00") })
            }
        }
        if (any) OutlinedButton(
            {
                Notifier.checkNow(context)
                Toast.makeText(context, "Controllo avviato", Toast.LENGTH_SHORT).show()
            },
            Modifier.fillMaxWidth().padding(16.dp, 4.dp, 16.dp, 16.dp)
        ) { Text("Controlla adesso") }
    }
}

@Composable
private fun UpdatesCard(settings: Settings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    val installed = remember { Updater.installed(context) }
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

    fun check() = scope.launch {
        state = UpdateState.Checking
        state = try {
            val r = Updater.latest()
            if (Updater.isNewer(r.version, installed)) UpdateState.Available(r) else UpdateState.UpToDate
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UpdateState.Failed((e as? ApiException)?.message ?: "Nessuna connessione. Riprova.")
        }
    }

    fun download(r: Release) = scope.launch {
        state = UpdateState.Downloading(r, 0f)
        state = try {
            val file = Updater.download(context, r) { p -> state = UpdateState.Downloading(r, p) }
            Updater.install(context, file)
            UpdateState.Ready(r, file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UpdateState.Failed((e as? ApiException)?.message ?: "Download non riuscito. Riprova.")
        }
    }

    LaunchedEffect(Unit) { check() }

    val size = { r: Release -> if (r.size > 0) " · " + Formatter.formatShortFileSize(context, r.size) else "" }
    val (title, subtitle) = when (val s = state) {
        UpdateState.Idle, UpdateState.Checking -> "Controllo in corso…" to "Installata: $installed"
        UpdateState.UpToDate -> "Hai l'ultima versione" to "Installata: $installed"
        is UpdateState.Available -> "Disponibile la versione ${s.release.version}" to "Installata: $installed${size(s.release)}"
        is UpdateState.Downloading -> "Download della versione ${s.release.version}" to "${(s.progress * 100).toInt()}%"
        is UpdateState.Ready -> "Versione ${s.release.version} scaricata" to "Premi Installa per completare"
        is UpdateState.Failed -> "Impossibile controllare" to s.message
    }
    Group {
        SettingRow(Icons.Rounded.SystemUpdate, title, subtitle, accent = true, trailing = {
            if (state is UpdateState.Checking || state is UpdateState.Idle) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        })
        Column(Modifier.padding(16.dp, 0.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            (state as? UpdateState.Available)?.release?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Column(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(c.surfaceContainerHigh).padding(14.dp, 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Novità", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    ExpandableText(
                        notes.lines().filter { it.trimStart().startsWith("- ") }.joinToString("\n") { "• " + it.trim().removePrefix("- ") }
                            .ifBlank { notes.replace(Regex("(?m)^#+\\s*"), "") },
                        lines = 6
                    )
                }
            }
            when (val s = state) {
                is UpdateState.Available -> Button({ download(s.release) }, Modifier.fillMaxWidth()) { Text("Scarica e installa") }
                is UpdateState.Downloading -> LinearProgressIndicator({ s.progress }, Modifier.fillMaxWidth())
                is UpdateState.Ready -> Button({ Updater.install(context, s.file) }, Modifier.fillMaxWidth()) { Text("Installa") }
                is UpdateState.UpToDate, is UpdateState.Failed -> OutlinedButton({ check() }, Modifier.fillMaxWidth()) { Text("Controlla di nuovo") }
                else -> {}
            }
        }
        HorizontalDivider(color = c.surfaceContainerHigh)
        SettingRow(
            Icons.Rounded.Notifications, "Controlla all'avvio", "Una volta al giorno, con un avviso in Home",
            onClick = { settings.updateAuto(!settings.autoUpdate) },
            trailing = { CheckSwitch(settings.autoUpdate) { settings.updateAuto(it) } }
        )
    }
}

@Composable
private fun StorageCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var offline by remember { mutableLongStateOf(-1L) }
    var downloads by remember { mutableLongStateOf(-1L) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(version) {
        withContext(Dispatchers.IO) {
            offline = Storage.offlineSize(context)
            downloads = Storage.downloadsSize(context)
        }
    }
    val fmt = { n: Long -> if (n < 0) "…" else Formatter.formatShortFileSize(context, n) }
    val clear: (suspend () -> Unit) -> Unit = { action ->
        scope.launch {
            withContext(Dispatchers.IO) { action() }
            version++
            Toast.makeText(context, "Spazio liberato", Toast.LENGTH_SHORT).show()
        }
    }
    Group {
        SettingRow(
            Icons.Rounded.Storage, "Dati salvati per l'uso offline", "${fmt(offline)} · voti, agenda, bacheca",
            trailing = { TextButton({ clear { Storage.clearOffline(context) } }, enabled = offline > 0) { Text("Svuota") } }
        )
        RowDivider()
        SettingRow(
            Icons.Rounded.Folder, "PDF e allegati scaricati", fmt(downloads),
            trailing = { TextButton({ clear { Storage.clearDownloads(context) } }, enabled = downloads > 0) { Text("Svuota") } }
        )
    }
}

@Composable
private fun NamesDialog(account: Account, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(Mentions.names(context, account).joinToString(", ")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Badge, null) },
        title = { Text("Nomi da cercare") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "L'app ti avvisa quando uno di questi nomi compare in agenda. Separali con una virgola: ad esempio il cognome e il cognome con l'iniziale del nome.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("Nomi") }, singleLine = true)
                Text(
                    "Se in classe c'è qualcuno con lo stesso cognome, aggiungi anche il nome per evitare avvisi sbagliati.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton({ Mentions.save(context, account, text); onDismiss() }) { Text("Salva") }
        },
        dismissButton = {
            TextButton({ Mentions.save(context, account, Mentions.defaultNames(account.name).joinToString(", ")); onDismiss() }) { Text("Ripristina") }
        }
    )
}

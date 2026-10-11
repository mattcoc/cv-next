package io.github.mattcoc.cvnext

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.withContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings as SettingsIcon
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.materialkolor.rememberDynamicColorScheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val target = mutableStateOf<String?>(null)
    private val targetAccount = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { enableEdgeToEdge() }
        val api = Api(applicationContext)
        val settings = Settings(applicationContext)
        Notifier.channels(this)
        Notifier.schedule(this, settings.alerts)
        Evening.schedule(this, settings)
        target.value = intent?.getStringExtra("section")
        targetAccount.value = intent?.getStringExtra("account")
        // Solo nella versione di sviluppo: apre direttamente una schermata, per gli screenshot automatici.
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val debugScreen = if (debuggable) intent?.getStringExtra("debugScreen") else null
        if (debuggable) intent?.getIntExtra("debugMode", -1)?.takeIf { it >= 0 }?.let { settings.update(mode = it) }
        if (debuggable) intent?.getStringExtra("debugSeed")?.let { name -> seeds.firstOrNull { it.second == name }?.let { settings.update(seed = it.first) } }
        setContent {
            AppTheme(settings) {
                Surface(Modifier.fillMaxSize()) {
                    when (debugScreen) {
                        "settings" -> SettingsScreen(settings) { finish() }
                        "grades" -> Box(Modifier.statusBarsPadding()) { GradesScreen(DebugData.grades, DebugData.periods) }
                        "today" -> Box(Modifier.statusBarsPadding()) {
                            TodayScreen(
                                "Matteo Rossi", School("Istituto Tecnico", "Padova PD"), DebugData.lessons, DebugData.grades, DebugData.agenda,
                                DebugData.absences, emptyList(), {}, {}, null, {}, {}, DebugData.reminders
                            ) {}
                        }
                        "absences" -> Box(Modifier.statusBarsPadding()) { AbsencesScreen(DebugData.absences) }
                        "agenda" -> Box(Modifier.statusBarsPadding()) { AgendaScreen(DebugData.agenda, "Tutto", DebugData.lessons) {} }
                        "subject" -> SubjectSheet("Matematica", DebugData.grades.filter { it.subject == "Matematica" }, DebugData.periods) {}
                        else -> App(api, settings, target, targetAccount)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        targetAccount.value = intent.getStringExtra("account")
        target.value = intent.getStringExtra("section")
    }
}

@Composable
fun AppTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val seeded = rememberDynamicColorScheme(Color(if (settings.seed == 0) seeds[1].first else settings.seed), dark, false)
    val colors = if (settings.seed == 0 && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else seeded
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let {
            WindowCompat.getInsetsController(it, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(32.dp)
    )
    MaterialTheme(colorScheme = colors, shapes = shapes, content = content)
}

@Composable
fun App(api: Api, settings: Settings, target: MutableState<String?>, targetAccount: MutableState<String?>) {
    val appContext = LocalContext.current
    var crash by remember { mutableStateOf(CrashLog.pending(appContext)) }
    crash?.let { details ->
        val close = { CrashLog.clear(appContext); crash = null }
        AlertDialog(
            onDismissRequest = close,
            title = { Text("L'app si è chiusa") },
            text = { Text("L'ultima volta CV Next si è chiusa per un errore. Vuoi aprire una segnalazione su GitHub con i dettagli tecnici? Potrai rileggerla prima di inviarla.") },
            confirmButton = { TextButton({ openLink(appContext, issueUrl(appContext, "L'app si è chiusa", details)); close() }) { Text("Segnala") } },
            dismissButton = { TextButton(close) { Text("Ignora") } }
        )
    }
    var current by remember { mutableStateOf(api.current) }
    var adding by remember { mutableStateOf(false) }
    LaunchedEffect(targetAccount.value) {
        targetAccount.value?.let { key ->
            if (key != current && api.accounts.any { it.key == key }) {
                api.switchTo(key)
                current = key
            }
        }
        targetAccount.value = null
    }
    val screen = if (current == null || adding) "login" else current!!
    AnimatedContent(
        screen,
        transitionSpec = { (fadeIn(tween(400)) + scaleIn(initialScale = 0.92f)) togetherWith (fadeOut(tween(200)) + scaleOut(targetScale = 1.05f)) },
        label = "app"
    ) { s ->
        if (s == "login") Login(api, if (current != null) ({ adding = false }) else null) { adding = false; current = api.current }
        else key(s) {
            Home(
                api, settings, target,
                onSwitch = { api.switchTo(it); current = it },
                onAdd = { adding = true },
                onLogout = { api.logout(); current = api.current }
            )
        }
    }
}

private fun Exception.text() = (this as? ApiException)?.message ?: "Nessuna connessione. Controlla internet e riprova."

@Composable
fun Login(api: Api, onBack: (() -> Unit)?, onDone: () -> Unit) {
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var show by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var choices by remember { mutableStateOf<List<Student>?>(null) }
    val canSubmit = !loading && user.isNotBlank() && pass.isNotEmpty()
    onBack?.let { BackHandler(onBack = it) }
    val login: (Student?) -> Unit = { student ->
        scope.launch {
            loading = true
            error = null
            try {
                api.login(user, pass, student)
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ChoiceNeeded) {
                choices = e.choices
            } catch (e: Exception) {
                error = e.text()
            }
            loading = false
        }
    }
    val submit: () -> Unit = { if (canSubmit) login(null) }
    choices?.let { list ->
        AlertDialog(
            onDismissRequest = { choices = null },
            confirmButton = {},
            dismissButton = { TextButton({ choices = null }) { Text("Annulla") } },
            title = { Text("Scegli lo studente") },
            text = {
                Column {
                    Text("Al tuo account sono collegati più studenti. Puoi aggiungere gli altri dopo, con \"Aggiungi account\".", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    list.forEach { st ->
                        ListItem(
                            headlineContent = { Text(st.name.ifBlank { st.ident }) },
                            supportingContent = st.school.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                            leadingContent = { Avatar(st.name.ifBlank { st.ident }) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable { choices = null; login(st) }
                        )
                    }
                }
            }
        )
    }
    Box(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        onBack?.let { IconButton(it, Modifier.padding(8.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro") } }
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            Text("CV Next", Modifier.appear(0), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(
                if (onBack != null) "Aggiungi un altro account" else "Accedi con le stesse credenziali del registro elettronico",
                Modifier.appear(1), color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                user, { user = it.trim(); error = null }, Modifier.fillMaxWidth().appear(2),
                label = { Text("Codice utente o email") }, singleLine = true, isError = error != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                pass, { pass = it; error = null }, Modifier.fillMaxWidth().appear(3),
                label = { Text("Password") }, singleLine = true, isError = error != null,
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton({ show = !show }) {
                        Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "Nascondi password" else "Mostra password")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() })
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(submit, Modifier.fillMaxWidth().height(52.dp).appear(4), enabled = canSubmit) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Accedi")
            }
            Column(Modifier.fillMaxWidth().appear(5), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text("Le credenziali restano cifrate solo sul tuo telefono", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("App non ufficiale. Non affiliata a Gruppo Spaggiari Parma S.p.A.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

class Res<T>(private val load: suspend () -> T) {
    var data by mutableStateOf<T?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set

    suspend fun refresh() {
        loading = true
        error = null
        if (data == null) try {
            data = withContext(CacheOnly) { load() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        try {
            data = load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.text()
        }
        loading = false
    }
}

/** [inBar] falso: la sezione si apre da Home e da Funzioni ma non ha un tasto nella barra in basso. */
enum class Section(val label: String, val icon: ImageVector, val selected: ImageVector, val inBar: Boolean = true) {
    Oggi("Home", Icons.Outlined.Home, Icons.Rounded.Home),
    Voti("Voti", Icons.Outlined.Grade, Icons.Rounded.Grade),
    Agenda("Agenda", Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth, inBar = false),
    Bacheca("Bacheca", Icons.Outlined.Campaign, Icons.Rounded.Campaign),
    Funzioni("Funzioni", Icons.Outlined.Apps, Icons.Rounded.Apps)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home(api: Api, settings: Settings, target: MutableState<String?>, onSwitch: (String) -> Unit, onAdd: () -> Unit, onLogout: () -> Unit) {
    var section by rememberSaveable { mutableStateOf(Section.Oggi) }
    var agendaFilter by rememberSaveable { mutableStateOf("Tutto") }
    var menu by remember { mutableStateOf(false) }
    val stack = remember { mutableStateListOf<Page>() }
    var depth by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val lessons = remember { Res { api.lessons(today.minusDays(30), today) } }
    val grades = remember { Res { api.grades() } }
    val agenda = remember { Res { api.agenda() } }
    val absences = remember { Res { api.absences() } }
    val notices = remember { Res { api.notices() } }
    val notes = remember { Res { api.notes() } }
    val didactics = remember { Res { api.didactics() } }
    val documents = remember { Res { api.documents() } }
    val periods = remember { Res { api.periods() } }
    val school = remember { Res { api.school() } }
    val payments = remember { Res { api.payments() } }
    LaunchedEffect(Unit) { listOf(lessons, grades, agenda, absences, notices, periods, school, payments).forEach { launch { it.refresh() } } }
    val context = LocalContext.current
    var update by remember { mutableStateOf<Release?>(null) }
    val reminders = remember { ReminderStore(context) }
    LaunchedEffect(Unit) { if (settings.autoUpdate) update = Updater.dailyCheck(context) }
    LaunchedEffect(target.value) {
        target.value?.let { name -> Section.entries.firstOrNull { it.name == name }?.let { stack.clear(); section = it } }
        target.value = null
    }
    BackHandler(section != Section.Oggi && stack.isEmpty()) { section = Section.Oggi }
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        AnimatedContent(
                            section.label,
                            transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                            label = "title"
                        ) { Text(it, fontWeight = FontWeight.SemiBold) }
                    },
                    navigationIcon = {
                        if (!section.inBar) IconButton({ section = Section.Oggi }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro") }
                    },
                    actions = {
                        IconButton({ stack.add(Page.Preferences) }) { Icon(Icons.Rounded.SettingsIcon, "Impostazioni") }
                        IconButton({ menu = true }, Modifier.semantics { contentDescription = "Account e profilo" }) { Avatar(api.name) }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                AnimatedContent(
                    section,
                    Modifier.fillMaxSize(),
                    transitionSpec = {
                        (fadeIn(tween(220, delayMillis = 90)) + scaleIn(tween(220, delayMillis = 90), initialScale = 0.96f)) togetherWith
                            fadeOut(tween(90))
                    },
                    label = "section"
                ) { s ->
                    when (s) {
                        Section.Oggi -> Refreshable(lessons, grades, agenda, absences) {
                            TodayScreen(
                                api.name, school.data, lessons.data!!, grades.data!!, agenda.data!!, absences.data!!, payments.data.orEmpty(),
                                { section = it }, { stack.add(Page.Web("PagOnline", WebLinks.payments)) },
                                update, { stack.add(Page.Preferences) }, { update = null },
                                reminders.of(api.current), { date, text -> api.current?.let { reminders.add(it, date, text) } }, { reminders.remove(it) },
                                api.accounts.firstOrNull { it.key == api.current }?.let { Mentions.names(context, it) }.orEmpty()
                            ) { stack.add(Page.Absences) }
                        }
                        Section.Voti -> Refreshable(grades) { GradesScreen(grades.data!!, periods.data.orEmpty()) }
                        Section.Agenda -> Refreshable(agenda) { AgendaScreen(agenda.data!!, agendaFilter, lessons.data.orEmpty()) { agendaFilter = it } }
                        Section.Bacheca -> Refreshable(notices) { NoticesScreen(notices.data!!) { stack.add(Page.NoticePage(it)) } }
                        Section.Funzioni -> FunctionsScreen({ stack.add(it) }, { agendaFilter = "Compiti"; section = Section.Agenda }) {
                            if (it == Section.Agenda) agendaFilter = "Tutto"
                            section = it
                        }
                    }
                }
                FloatingNavBar(section, Modifier.align(Alignment.BottomCenter)) { section = it }
            }
        }
        val back: () -> Unit = { stack.removeLastOrNull() }
        AnimatedContent(
            stack.lastOrNull(),
            transitionSpec = {
                val forward = targetState != null && (initialState == null || stack.size > depth)
                depth = stack.size
                if (forward) (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(240))) togetherWith fadeOut(tween(160))
                else fadeIn(tween(200)) togetherWith (slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(200)))
            },
            label = "page"
        ) { p ->
            if (p != null) Surface(Modifier.fillMaxSize()) {
                when (p) {
                    is Page.NoticePage -> NoticeScreen(api, p.notice) { back(); scope.launch { notices.refresh() } }
                    is Page.FilePage -> FileScreen(p.title, p.file, back)
                    is Page.Web -> WebScreen(api, p.title, p.url, back)
                    Page.Absences -> SubScreen("Assenze", back) { Refreshable(absences) { AbsencesScreen(absences.data!!) } }
                    Page.Notes -> SubScreen("Note", back) { Lazy(notes) { NotesScreen(notes.data!!) } }
                    Page.Didactics -> SubScreen("File dei docenti", back) { Lazy(didactics) { DidacticsScreen(api, didactics.data!!) { stack.add(it) } } }
                    Page.Documents -> SubScreen("Scrutini", back) { Lazy(documents) { DocumentsScreen(api, documents.data!!) { stack.add(it) } } }
                    Page.Lessons -> SubScreen("Lezioni", back) { Refreshable(lessons) { LessonsScreen(lessons.data!!) } }
                    Page.Preferences -> SettingsScreen(settings, api, back)
                }
            }
        }
    }
    if (menu) AccountSheet(
        api, school.data, { menu = false }, { menu = false; onSwitch(it) }, { menu = false; onAdd() },
        { menu = false; onLogout() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(api: Api, school: School?, onDismiss: () -> Unit, onSwitch: (String) -> Unit, onAdd: () -> Unit, onLogout: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val clear = ListItemDefaults.colors(containerColor = Color.Transparent)
    ModalBottomSheet(onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(24.dp, 8.dp, 24.dp, 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(api.name, 64.dp, Modifier.appear(0))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.appear(1)) {
                    Text(api.name.pretty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(api.uid, color = c.onSurfaceVariant)
                    school?.let {
                        Text(listOf(it.name, it.city).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = c.primary)
                    }
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 24.dp))
            Text("Account", Modifier.padding(24.dp, 16.dp, 24.dp, 4.dp), style = MaterialTheme.typography.titleSmall, color = c.primary)
            api.accounts.forEachIndexed { i, a ->
                val active = a.key == api.current
                ListItem(
                    headlineContent = { Text(a.name.pretty()) },
                    supportingContent = { Text(if (active) "In uso" else "Tocca per passare a questo account") },
                    leadingContent = { Avatar(a.name) },
                    trailingContent = if (active) { { Icon(Icons.Rounded.Check, null, tint = c.primary) } } else null,
                    colors = clear,
                    modifier = Modifier.appear(i + 2).clickable(enabled = !active) { onSwitch(a.key) }.padding(horizontal = 8.dp)
                )
            }
            ListItem(
                headlineContent = { Text("Aggiungi account") },
                leadingContent = { Icon(Icons.Rounded.PersonAdd, null) },
                colors = clear,
                modifier = Modifier.appear(7).clickable(onClick = onAdd).padding(horizontal = 8.dp)
            )
            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            ListItem(
                headlineContent = { Text("Esci da questo account", color = c.error) },
                leadingContent = { Icon(Icons.AutoMirrored.Rounded.Logout, null, tint = c.error) },
                colors = clear,
                modifier = Modifier.appear(8).clickable(onClick = onLogout).padding(horizontal = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Refreshable(vararg res: Res<*>, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val retry: () -> Unit = { res.forEach { scope.launch { it.refresh() } } }
    val ready = res.all { it.data != null }
    PullToRefreshBox(ready && res.any { it.loading }, retry, Modifier.fillMaxSize()) {
        val error = res.firstNotNullOfOrNull { it.error }
        when {
            ready && error != null -> Column(Modifier.fillMaxSize()) {
                OfflineBanner(error, retry)
                Box(Modifier.weight(1f)) { content() }
            }
            ready -> content()
            error != null -> Message(error, retry)
            else -> LoadingState()
        }
    }
}

@Composable
fun OfflineBanner(error: String, retry: () -> Unit) = Row(
    Modifier.fillMaxWidth().padding(16.dp, 4.dp, 16.dp, 4.dp).clip(MaterialTheme.shapes.medium)
        .background(MaterialTheme.colorScheme.secondaryContainer).clickable(onClick = retry).padding(14.dp, 10.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Icon(Icons.Rounded.CloudOff, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
        Text("Dati dell'ultimo aggiornamento", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
    Text("Riprova", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun Lazy(res: Res<*>, content: @Composable () -> Unit) {
    LaunchedEffect(res) { if (res.data == null && !res.loading) res.refresh() }
    Refreshable(res, content = content)
}

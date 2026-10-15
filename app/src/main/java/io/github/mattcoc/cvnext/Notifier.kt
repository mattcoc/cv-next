package io.github.mattcoc.cvnext

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as SystemSettings
import android.net.Uri
import android.content.ComponentName
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** [plural] dà il titolo del riepilogo quando ne arrivano più di una ("3 nuovi voti"); [list] è la schermata che lo apre. */
enum class Alert(val title: String, val description: String, val single: String, val section: String, val plural: (Int) -> String, val list: String) {
    Notices("Bacheca", "Nuove comunicazioni e circolari", "Nuova comunicazione", "Bacheca", { "$it nuove circolari" }, "Bacheca"),
    Grades("Voti", "Quando viene inserito un voto", "Nuovo voto", "Voti", { "$it nuovi voti" }, "Voti"),
    Homework("Agenda", "Nuovi compiti ed eventi in agenda", "Nuovo in agenda", "Agenda", { "$it novità in agenda" }, "Agenda"),
    Didactics("Materiale dei docenti", "Nuovi file condivisi dai docenti", "Nuovo materiale", "Funzioni", { "$it nuovi file dei docenti" }, "Didactics"),
    Notes("Note", "Note disciplinari e annotazioni", "Nuova nota", "Funzioni", { "$it nuove note" }, "Notes"),
    Payments("PagOnline", "Pagamenti da effettuare", "Pagamento da fare", "Funzioni", { "$it pagamenti da fare" }, "Payments"),
    Mentions("Il tuo nome", "Quando sei nominato in agenda, ad esempio tra gli interrogati", "Sei nominato in agenda", "Oggi", { "Sei nominato $it volte in agenda" }, "Oggi")
}

/** Una novità da notificare: [target] è la schermata che si apre toccandola. */
data class News(val id: String, val title: String, val text: String, val target: String)

/** "oggi", "domani" o la data per esteso, da mettere dopo "per". */
fun whenLabel(date: String, today: LocalDate = LocalDate.now()): String = runCatching {
    when (LocalDate.parse(date.take(10))) {
        today -> "oggi"
        today.plusDays(1) -> "domani"
        else -> longDate(date).replaceFirstChar { it.lowercase() }
    }
}.getOrDefault(date)

/**
 * Con l'ottimizzazione della batteria attiva (e su Xiaomi senza "avvio automatico") il sistema
 * rimanda o salta i controlli in background: le notifiche arrivano solo aprendo l'app.
 */
object Battery {
    fun restricted(context: Context): Boolean = runCatching {
        !context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)

    /** Finestra di sistema che chiede di lasciar funzionare l'app in background. */
    fun request(context: Context): Intent =
        Intent(SystemSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    val xiaomi: Boolean get() = Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco")

    /** Pagina "Avvio automatico" di MIUI e HyperOS; se non esiste, le informazioni dell'app. */
    fun autostart(context: Context): List<Intent> = listOf(
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    )

    fun open(context: Context, intents: List<Intent>) {
        for (i in intents) if (runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}

object Notifier {
    fun schedule(context: Context, enabled: Set<Alert>) {
        runCatching {
            val wm = WorkManager.getInstance(context)
            if (enabled.isEmpty() && !HomeworkWidget.hasWidgets(context)) {
                wm.cancelUniqueWork("checks")
                return
            }
            val request = PeriodicWorkRequestBuilder<CheckWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            // "check" era il nome usato prima del cambio di pacchetto: quel lavoro puntava a una classe che non esiste più.
            wm.cancelUniqueWork("check")
            wm.enqueueUniquePeriodicWork("checks", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    fun checkNow(context: Context) {
        runCatching {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<CheckWorker>().build())
        }
    }

    fun channels(context: Context) {
        runCatching {
            if (Build.VERSION.SDK_INT < 26) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            Alert.entries.forEach { manager.createNotificationChannel(NotificationChannel(it.name, it.title, NotificationManager.IMPORTANCE_DEFAULT).apply { description = it.description }) }
        }
    }

    private fun allowed(context: Context) =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun intent(context: Context, account: String, target: String, code: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra("section", target)
            .putExtra("account", account)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun code(alert: Alert, account: String, id: String) = "${alert.name}-$account-$id".hashCode()
    private fun group(alert: Alert, account: String) = "${alert.name}-$account"
    private val ACCENT = 0xFF0072DE.toInt()

    /**
     * Notifica le novità di un tipo: ognuna ha la sua notifica (il tocco apre proprio quella circolare o quel voto)
     * e, quando sono più di una, un riepilogo le raggruppa. Suona una volta sola per ogni controllo.
     * [who] è il nome dell'account quando sul telefono ce n'è più di uno; [more] le novità oltre quelle mostrate.
     */
    fun post(context: Context, alert: Alert, account: String, who: String?, news: List<News>, more: Int = 0) {
        runCatching {
            if (news.isEmpty() || !allowed(context)) return
            val manager = NotificationManagerCompat.from(context)
            news.forEachIndexed { i, n ->
                val notification = NotificationCompat.Builder(context, alert.name)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setColor(ACCENT)
                    .setContentTitle(n.title)
                    .setContentText(n.text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(n.text))
                    .setSubText(who ?: alert.title)
                    .setCategory(NotificationCompat.CATEGORY_EVENT)
                    .setContentIntent(intent(context, account, n.target, code(alert, account, n.id)))
                    .setAutoCancel(true)
                    .setGroup(group(alert, account))
                    .setSilent(i > 0)
                    .build()
                manager.notify(code(alert, account, n.id), notification)
            }
            summary(context, alert, account, who, more)
        }
    }

    /** Il riepilogo del gruppo, rifatto con le notifiche ancora visibili: sparisce quando ne resta una sola. */
    private fun summary(context: Context, alert: Alert, account: String, who: String?, more: Int = 0) {
        val key = group(alert, account)
        val id = code(alert, account, "summary")
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val shown = manager.activeNotifications.filter { it.notification.group == key && it.id != id }
        val total = shown.size + more
        if (total < 2) { manager.cancel(id); return }
        val titles = shown.sortedByDescending { it.postTime }.mapNotNull { it.notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE) }
        val inbox = NotificationCompat.InboxStyle().setBigContentTitle(alert.plural(total))
        titles.take(6).forEach { inbox.addLine(it) }
        if (total > 6) inbox.setSummaryText("e altre ${total - 6}")
        val notification = NotificationCompat.Builder(context, alert.name)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(ACCENT)
            .setContentTitle(alert.plural(total))
            .setContentText(titles.take(3).joinToString(" · "))
            .setStyle(inbox)
            .setSubText(who ?: alert.title)
            .setNumber(total)
            .setContentIntent(intent(context, account, alert.list, id))
            .setAutoCancel(true)
            .setGroup(key)
            .setGroupSummary(true)
            .setSilent(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /** Toglie le notifiche delle circolari già lette nell'app (anche con "Segna tutte come lette"). */
    fun dismiss(context: Context, alert: Alert, account: String?, ids: List<String>) {
        runCatching {
            account ?: return
            val manager = NotificationManagerCompat.from(context)
            ids.forEach { manager.cancel(code(alert, account, it)) }
            summary(context, alert, account, null)
        }
    }
}

class CheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = Settings(applicationContext)
        val accounts = Api(applicationContext).accounts
        val prefs = applicationContext.getSharedPreferences("seen", Context.MODE_PRIVATE)
        Notifier.channels(applicationContext)
        accounts.forEach { account ->
            val api = Api(applicationContext, account.key)
            val who = if (accounts.size > 1) account.name.substringBefore(' ').pretty() else null
            settings.alerts.forEach { alert ->
                runCatching {
                    val items = fetch(api, alert, account)
                    val key = "${alert.name}-${account.key}"
                    val seen = prefs.getStringSet(key, null)
                    if (seen != null) {
                        val new = items.filter { it.id !in seen }
                        Notifier.post(applicationContext, alert, account.key, who, new.take(5), (new.size - 5).coerceAtLeast(0))
                    }
                    // Tiene solo gli id ancora presenti, così l'elenco non cresce all'infinito.
                    prefs.edit().putStringSet(key, items.map { it.id }.toSet()).apply()
                }
            }
        }
        prefs.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
        runCatching { HomeworkWidget.refresh(applicationContext) }
        return Result.success()
    }

    private suspend fun fetch(api: Api, alert: Alert, account: Account): List<News> = when (alert) {
        Alert.Notices -> api.notices().filter { !it.read }.map { n ->
            val pdf = when (n.attachments.size) { 0 -> ""; 1 -> "1 PDF allegato"; else -> "${n.attachments.size} PDF allegati" }
            News("${n.id}", n.title, listOf(n.category.ifBlank { "Nuova circolare" }, pdf).filter(String::isNotBlank).joinToString(" · "), "notice:${n.id}")
        }
        Alert.Grades -> api.grades().map { g ->
            val id = if (g.id != 0L) "${g.id}" else "${g.subject}_${g.date}_${g.value}"
            val details = listOf(g.type.replaceFirstChar { it.uppercase() }, shortDate(g.date), if (g.counts) "" else "non fa media", g.notes)
            News(id, "${g.subject}: ${g.value}", details.filter(String::isNotBlank).joinToString(" · "), "Voti")
        }
        Alert.Homework -> api.agenda().map { e ->
            val what = if (e.isTest) e.testKind else if (e.homework) "Compito" else "Evento"
            val subject = e.subject.ifBlank { e.author }
            News(
                "${e.id}", "$what per ${whenLabel(e.date)}" + if (subject.isNotBlank()) " · $subject" else "", e.notes.tidy().ifBlank { "Nessuna descrizione" },
                if (e.isTest) "Tests" else if (e.homework) "Compiti" else "Agenda"
            )
        }
        Alert.Didactics -> api.didactics().flatMap { t -> t.folders.flatMap { f -> f.contents.map {
            News("${it.id}", "Nuovo file da ${t.name}", listOf(it.name, f.name).filter(String::isNotBlank).distinct().joinToString(" · "), "Didactics")
        } } }
        Alert.Notes -> api.notes().map { News("${it.id}", "${it.type} · ${it.author}", it.text.ifBlank { longDate(it.date) }, "Notes") }
        Alert.Payments -> api.payments().map { News(it.id, "Da pagare: € ${it.amount}", "${it.title} · entro il ${shortDate(it.due)}", "Payments") }
        Alert.Mentions -> {
            val names = Mentions.names(applicationContext, account)
            api.agenda().filter { it.date >= LocalDate.now().toString() && Mentions.matches("${it.notes} ${it.subject}", names) }.map {
                val what = if (it.isTest) it.testKind else if (it.homework) "Compito" else "Evento"
                val subject = it.subject.ifBlank { it.author }
                News("m${it.id}", "Sei nominato: $what per ${whenLabel(it.date)}" + if (subject.isNotBlank()) " · $subject" else "", it.notes.tidy(), if (it.isTest) "Tests" else "Giornata")
            }
        }
    }
}

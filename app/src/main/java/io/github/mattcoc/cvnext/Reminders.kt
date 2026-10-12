package io.github.mattcoc.cvnext

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Promemoria scritti dall'utente su un giorno. Restano solo sul telefono, separati per account. */
data class Reminder(val id: String, val account: String, val date: String, val text: String)

class ReminderStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    var all by mutableStateOf(load())
        private set

    private fun load(): List<Reminder> = runCatching {
        JSONArray(prefs.getString("items", "[]")).let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }.map {
                Reminder(it.getString("id"), it.getString("account"), it.getString("date"), it.getString("text"))
            }
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<Reminder>) {
        all = list.sortedBy { it.date }
        val a = JSONArray()
        all.forEach { a.put(JSONObject().put("id", it.id).put("account", it.account).put("date", it.date).put("text", it.text)) }
        prefs.edit().putString("items", a.toString()).apply()
    }

    fun of(account: String?) = all.filter { it.account == account }

    fun add(account: String, date: String, text: String) {
        if (text.isBlank()) return
        save(all + Reminder(UUID.randomUUID().toString(), account, date, text.trim()))
    }

    fun remove(r: Reminder) = save(all.filter { it.id != r.id })

    /** I promemoria passati da più di un mese vengono tolti. */
    fun prune() = save(all.filter { it.date >= LocalDate.now().minusDays(30).toString() })
}

/** Ogni sera, all'ora scelta, avvisa dei compiti, delle verifiche e dei promemoria del giorno di scuola successivo. */
object Evening {
    const val CHANNEL = "Evening"
    private const val WORK = "evening"

    fun nextSchoolDay(from: LocalDate = LocalDate.now()): LocalDate =
        from.plusDays(1).let { if (it.dayOfWeek == DayOfWeek.SUNDAY) it.plusDays(1) else it }

    fun schedule(context: Context, settings: Settings) {
        runCatching {
            val wm = WorkManager.getInstance(context)
            if (!settings.evening) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(settings.eveningHour, 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<EveningWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }

    fun channel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Promemoria della sera", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Compiti, verifiche e promemoria del giorno dopo" }
        )
    }
}

class EveningWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }
        Evening.channel(ctx)
        val day = Evening.nextSchoolDay()
        val label = if (day == LocalDate.now().plusDays(1)) "Domani"
        else day.format(DateTimeFormatter.ofPattern("EEEE", Locale.ITALIAN)).replaceFirstChar { it.uppercase() }
        val reminders = ReminderStore(ctx)
        val accounts = Api(ctx).accounts
        accounts.forEach { account ->
            val api = Api(ctx, account.key)
            val events = runCatching { api.agenda() }.getOrNull()
                ?: runCatching { withContext(CacheOnly) { api.agenda() } }.getOrNull().orEmpty()
            val dayEvents = events.filter { it.date == day.toString() && (it.homework || it.isTest) }
            val mine = reminders.of(account.key).filter { it.date == day.toString() }
            if (dayEvents.isEmpty() && mine.isEmpty()) return@forEach
            val tests = dayEvents.count { it.isTest }
            val homework = dayEvents.count { it.homework && !it.isTest }
            val title = buildList {
                if (tests > 0) add(if (tests == 1) "1 verifica" else "$tests verifiche")
                if (homework > 0) add(if (homework == 1) "1 compito" else "$homework compiti")
                if (mine.isNotEmpty()) add(if (mine.size == 1) "1 promemoria" else "${mine.size} promemoria")
            }.joinToString(", ")
            val who = if (accounts.size > 1) " · ${account.name.substringBefore(' ').pretty()}" else ""
            val lines = dayEvents.map { e -> (if (e.isTest) "Verifica · " else "") + "${e.subject.ifBlank { e.author }}: ${e.notes}" } +
                mine.map { "• ${it.text}" }
            val intent = Intent(ctx, MainActivity::class.java)
                .putExtra("section", Section.Giornata.name).putExtra("account", account.key)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(ctx, "evening-${account.key}".hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(ctx, Evening.CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("$label: $title$who")
                .setContentText(lines.first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(ctx).notify("evening-${account.key}".hashCode(), notification) }
        }
        reminders.prune()
        return Result.success()
    }
}

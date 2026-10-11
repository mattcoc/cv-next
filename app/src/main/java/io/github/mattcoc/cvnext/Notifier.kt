package io.github.mattcoc.cvnext

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import java.util.concurrent.TimeUnit

enum class Alert(val title: String, val description: String, val single: String, val section: String) {
    Notices("Bacheca", "Nuove comunicazioni e circolari", "Nuova comunicazione", "Bacheca"),
    Grades("Voti", "Quando viene inserito un voto", "Nuovo voto", "Voti"),
    Homework("Agenda", "Nuovi compiti ed eventi in agenda", "Nuovo in agenda", "Agenda"),
    Didactics("Materiale dei docenti", "Nuovi file condivisi dai docenti", "Nuovo materiale", "Funzioni"),
    Notes("Note", "Note disciplinari e annotazioni", "Nuova nota", "Funzioni"),
    Payments("PagOnline", "Pagamenti da effettuare", "Pagamento da fare", "Funzioni")
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

    fun post(context: Context, alert: Alert, account: String, id: String, text: String) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("section", alert.section)
                .putExtra("account", account)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(context, "${alert.name}-$account".hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(context, alert.name)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(alert.single)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setGroup("${alert.name}-$account")
                .build()
            NotificationManagerCompat.from(context).notify("${alert.name}-$account-$id".hashCode(), notification)
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
            val who = if (accounts.size > 1) "${account.name.substringBefore(' ').pretty()} · " else ""
            settings.alerts.forEach { alert ->
                runCatching {
                    val items = fetch(api, alert)
                    val key = "${alert.name}-${account.key}"
                    val seen = prefs.getStringSet(key, null)
                    if (seen != null) items.filter { it.first !in seen }.take(5).forEach {
                        Notifier.post(applicationContext, alert, account.key, it.first, who + it.second)
                    }
                    // Tiene solo gli id ancora presenti, così l'elenco non cresce all'infinito.
                    prefs.edit().putStringSet(key, items.map { it.first }.toSet()).apply()
                }
            }
        }
        prefs.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
        runCatching { HomeworkWidget.refresh(applicationContext) }
        return Result.success()
    }

    private suspend fun fetch(api: Api, alert: Alert): List<Pair<String, String>> = when (alert) {
        Alert.Notices -> api.notices().filter { !it.read }.map { "${it.id}" to it.title }
        Alert.Grades -> api.grades().map { g ->
            val id = if (g.id != 0L) "${g.id}" else "${g.subject}_${g.date}_${g.value}"
            id to "${g.subject}: ${g.value}" + if (g.type.isNotBlank()) " (${g.type.lowercase()})" else ""
        }
        Alert.Homework -> api.agenda().map {
            "${it.id}" to "${shortDate(it.date)} · ${it.subject.ifBlank { it.author }}: ${it.notes}"
        }
        Alert.Didactics -> api.didactics().flatMap { t -> t.folders.flatMap { f -> f.contents.map { "${it.id}" to "${t.name}: ${it.name}" } } }
        Alert.Notes -> api.notes().map { "${it.id}" to "${it.type} · ${it.author}" }
        Alert.Payments -> api.payments().map { it.id to "${it.title} · € ${it.amount} entro il ${shortDate(it.due)}" }
    }
}

package com.example.classevivamodernaemeglio

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Widget con i prossimi compiti dell'account attivo. Si aggiorna insieme al controllo delle notifiche. */
class HomeworkWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Notifier.schedule(context, Settings(context).alerts)
        Notifier.checkNow(context)
    }

    override fun onEnabled(context: Context) = Notifier.schedule(context, Settings(context).alerts)

    override fun onDisabled(context: Context) = Notifier.schedule(context, Settings(context).alerts)

    companion object {
        private fun ids(context: Context) =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, HomeworkWidget::class.java))

        fun hasWidgets(context: Context) = runCatching { ids(context).isNotEmpty() }.getOrDefault(false)

        suspend fun refresh(context: Context) {
            val ids = ids(context)
            if (ids.isEmpty()) return
            val api = Api(context)
            if (api.current == null) {
                update(context, ids, null, "Accedi all'app per vedere i compiti")
                return
            }
            val events = runCatching { api.agenda() }.getOrNull()
                ?: runCatching { withContext(CacheOnly) { api.agenda() } }.getOrNull()
            val today = LocalDate.now().toString()
            val homework = events.orEmpty().filter { it.homework && it.date >= today }
            val text = when {
                events == null -> "Impossibile aggiornare i compiti"
                homework.isEmpty() -> "Nessun compito in programma"
                else -> homework.take(6).joinToString("\n") { e ->
                    val what = e.notes.replace(Regex("\\s+"), " ").trim().let { if (it.length > 70) it.take(69) + "…" else it }
                    "${shortDate(e.date)} · ${e.subject.ifBlank { e.author }}: $what"
                }
            }
            val who = if (api.accounts.size > 1) api.name.substringBefore(' ').pretty() else null
            update(context, ids, who, text)
        }

        private fun update(context: Context, ids: IntArray, who: String?, text: String) {
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("section", Section.Agenda.name)
                .putExtra("account", Api(context).current)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(context, 7, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val views = RemoteViews(context.packageName, R.layout.widget_homework).apply {
                setTextViewText(R.id.widget_title, if (who != null) "Compiti · $who" else "Compiti")
                setTextViewText(R.id.widget_list, text)
                setOnClickPendingIntent(R.id.widget_root, pending)
            }
            AppWidgetManager.getInstance(context).updateAppWidget(ids, views)
        }
    }
}

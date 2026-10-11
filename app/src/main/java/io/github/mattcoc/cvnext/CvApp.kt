package io.github.mattcoc.cvnext

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import java.io.File

class CvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}

const val REPO = "https://github.com/mattcoc/cv-next"

/** Indirizzo per aprire una segnalazione su GitHub già compilata con versione e telefono. */
fun issueUrl(context: Context, title: String = "", details: String = ""): String {
    val device = "CV Next ${Updater.installed(context)} · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}"
    val body = buildString {
        append("**Cosa è successo**\n\n\n**Cosa ti aspettavi**\n\n\n---\n")
        append(device)
        if (details.isNotBlank()) append("\n\n<details><summary>Dettagli tecnici</summary>\n\n```\n").append(details).append("\n```\n</details>")
    }
    return "$REPO/issues/new?title=${Uri.encode(title)}&body=${Uri.encode(body)}"
}

/**
 * Se l'app si chiude per un errore, salva il dettaglio sul telefono (nient'altro, e non lo invia a nessuno):
 * al riavvio l'utente può scegliere di aprire una segnalazione su GitHub già compilata.
 */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { file(context).writeText(error.stackTraceToString().take(3000)) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun pending(context: Context): String? = runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}

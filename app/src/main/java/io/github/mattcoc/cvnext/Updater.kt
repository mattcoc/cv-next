package io.github.mattcoc.cvnext

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

data class Release(val version: String, val apkUrl: String, val size: Long, val notes: String = "")

object Updater {

    fun installed(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    suspend fun latest(): Release = withContext(Dispatchers.IO) {
        val c = URI(Endpoints.RELEASES).toURL().openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        val code = c.responseCode
        val body = (if (code < 400) c.inputStream else c.errorStream)?.use { String(it.readBytes()) }.orEmpty()
        c.disconnect()
        if (code != 200) throw ApiException("Impossibile controllare gli aggiornamenti (errore $code)")
        val json = JSONObject(body)
        val assets = json.optJSONArray("assets")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
        val apk = assets.firstOrNull { it.optString("name").endsWith(".apk", true) }
            ?: throw ApiException("L'ultima versione non contiene un APK")
        Release(
            json.optString("tag_name").removePrefix("v"), apk.getString("browser_download_url"), apk.optLong("size"),
            json.optString("body").trim()
        )
    }

    /** Controllo automatico all'avvio, al massimo una volta al giorno. Restituisce la versione nuova se c'è. */
    suspend fun dailyCheck(context: Context): Release? {
        val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
        val installed = installed(context)
        val saved = prefs.getString("release", null)?.let {
            runCatching { JSONObject(it).let { j -> Release(j.getString("v"), j.getString("u"), j.optLong("s"), j.optString("n")) } }.getOrNull()
        }
        if (System.currentTimeMillis() - prefs.getLong("checked", 0) < 24 * 3600 * 1000L) {
            return saved?.takeIf { isNewer(it.version, installed) }
        }
        val r = runCatching { latest() }.getOrNull() ?: return saved?.takeIf { isNewer(it.version, installed) }
        prefs.edit()
            .putLong("checked", System.currentTimeMillis())
            .putString("release", JSONObject().put("v", r.version).put("u", r.apkUrl).put("s", r.size).put("n", r.notes).toString())
            .apply()
        return r.takeIf { isNewer(it.version, installed) }
    }

    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.').map { it.toIntOrNull() ?: 0 }
        val l = local.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val d = r.getOrElse(i) { 0 } - l.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    suspend fun download(context: Context, release: Release, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, "cv-next-${release.version}.apk")
        val c = URI(release.apkUrl).toURL().openConnection() as HttpURLConnection
        try {
            if (c.responseCode != 200) throw ApiException("Download non riuscito (errore ${c.responseCode})")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: release.size
            c.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) progress(done.toFloat() / total)
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        file
    }

    fun install(context: Context, file: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(context, "Consenti a CV Next di installare app, poi torna indietro e premi Installa", Toast.LENGTH_LONG).show()
            context.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

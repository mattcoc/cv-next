package com.example.classevivamodernaemeglio

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

data class Release(val version: String, val apkUrl: String, val size: Long)

object Updater {
    private const val LATEST = "https://api.github.com/repos/mattcoc/cv-next/releases/latest"

    fun installed(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    suspend fun latest(): Release = withContext(Dispatchers.IO) {
        val c = URI(LATEST).toURL().openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        val code = c.responseCode
        val body = (if (code < 400) c.inputStream else c.errorStream)?.use { String(it.readBytes()) }.orEmpty()
        c.disconnect()
        if (code != 200) throw ApiException("Impossibile controllare gli aggiornamenti (errore $code)")
        val json = JSONObject(body)
        val assets = json.optJSONArray("assets")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
        val apk = assets.firstOrNull { it.optString("name").endsWith(".apk", true) }
            ?: throw ApiException("L'ultima versione non contiene un APK")
        Release(json.optString("tag_name").removePrefix("v"), apk.getString("browser_download_url"), apk.optLong("size"))
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
fun UpdateCard(modifier: Modifier = Modifier) {
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

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = c.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Versione installata: ${installed.ifBlank { "–" }}", fontWeight = FontWeight.Medium)
                    val status = when (val s = state) {
                        UpdateState.Idle -> "Gli aggiornamenti arrivano da GitHub"
                        UpdateState.Checking -> "Controllo in corso…"
                        UpdateState.UpToDate -> "Hai già l'ultima versione"
                        is UpdateState.Available -> "Disponibile la versione ${s.release.version}" +
                            if (s.release.size > 0) " (${s.release.size / (1024 * 1024)} MB)" else ""
                        is UpdateState.Downloading -> "Download della versione ${s.release.version}… ${(s.progress * 100).toInt()}%"
                        is UpdateState.Ready -> "Versione ${s.release.version} scaricata"
                        is UpdateState.Failed -> s.message
                    }
                    Text(
                        status, style = MaterialTheme.typography.bodySmall,
                        color = if (state is UpdateState.Failed) c.error else c.onSurfaceVariant
                    )
                }
            }
            when (val s = state) {
                is UpdateState.Downloading -> LinearProgressIndicator({ s.progress }, Modifier.fillMaxWidth())
                is UpdateState.Available -> FilledTonalButton({ download(s.release) }, Modifier.fillMaxWidth()) { Text("Scarica e installa") }
                is UpdateState.Ready -> FilledTonalButton({ Updater.install(context, s.file) }, Modifier.fillMaxWidth()) { Text("Installa") }
                UpdateState.Checking -> FilledTonalButton({}, Modifier.fillMaxWidth(), enabled = false) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                else -> FilledTonalButton({ check() }, Modifier.fillMaxWidth()) { Text("Controlla aggiornamenti") }
            }
        }
    }
}

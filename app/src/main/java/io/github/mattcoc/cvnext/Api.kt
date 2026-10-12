package io.github.mattcoc.cvnext

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class ApiException(message: String) : Exception(message)

/** Il login ha più studenti collegati (genitore con più figli): bisogna sceglierne uno. */
class ChoiceNeeded(val choices: List<Student>) : Exception("Scegli lo studente")

data class Student(val ident: String, val name: String, val school: String)

/** [key] distingue gli studenti che condividono le stesse credenziali. */
data class Account(val key: String, val uid: String, val name: String)

/** Nel contesto di [CacheOnly] le chiamate leggono solo i dati salvati, senza usare la rete. */
object CacheOnly : AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<CacheOnly>
}

class Api(context: Context, private val fixed: String? = null) {
    private val store = SecureStore.get(context)
    private val cacheRoot = File(context.noBackupFilesDir, "api")
    private val mutex = Mutex()

    private fun JSONObject.key() = optString("key").ifBlank { getString("uid") }

    private fun stored(): List<JSONObject> = runCatching {
        JSONArray(store.get("accounts") ?: "[]").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    }.getOrDefault(emptyList())

    private fun save(list: List<JSONObject>) = store.put("accounts", JSONArray().also { a -> list.forEach { a.put(it) } }.toString())

    private fun account() = stored().firstOrNull { it.key() == current }

    val accounts get() = stored().map { Account(it.key(), it.getString("uid"), it.optString("name")) }
    val current get() = fixed ?: store.get("current")
    val name get() = account()?.optString("name").orEmpty()
    val uid get() = account()?.optString("uid").orEmpty()

    private fun token() = current?.let { store.get("token|$it") }
    private fun id() = current?.let { store.get("id|$it") }.orEmpty()

    /** Accede e salva l'account. Se ci sono più studenti e [student] è nullo lancia [ChoiceNeeded]. */
    suspend fun login(uid: String, pass: String, student: Student? = null) = withContext(Dispatchers.IO) {
        val (res, _) = auth(uid, pass, student?.ident, pickFirst = false)
        val key = if (student != null) "$uid|${student.ident}" else uid
        val name = student?.name?.takeIf { it.isNotBlank() } ?: "${res.optString("firstName")} ${res.optString("lastName")}".trim()
        val account = JSONObject().put("key", key).put("uid", uid).put("pass", pass).put("name", name)
        if (student != null) account.put("ident", student.ident)
        save(stored().filter { it.key() != key } + account)
        store.put("current", key)
        saveSession(key, res)
    }

    private fun auth(uid: String, pass: String, ident: String?, pickFirst: Boolean): Pair<JSONObject, String?> {
        val body = JSONObject().put("ident", ident ?: JSONObject.NULL).put("pass", pass).put("uid", uid)
        var r = request("POST", "/auth/login", body.toString(), null)
        var used = ident
        if (r.second.optString("requestedAction") == "choiceIdent") {
            val choices = r.second.optJSONArray("choices")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
                .map { Student(it.optString("ident"), it.optString("name").pretty(), it.optString("school").pretty()) }
            if (choices.isEmpty()) throw ApiException("Nessuno studente collegato all'account")
            if (choices.size > 1 && !pickFirst) throw ChoiceNeeded(choices)
            used = choices.first().ident
            body.put("ident", used)
            r = request("POST", "/auth/login", body.toString(), null)
        }
        if (!r.second.has("token")) throw ApiException(if (r.first == 422) "Credenziali errate" else "Errore ${r.first}")
        return r.second to used
    }

    private fun saveSession(key: String, res: JSONObject) {
        store.put("token|$key", res.getString("token"))
        store.put("id|$key", res.optString("ident").filter { it.isDigit() })
    }

    fun switchTo(key: String) = store.put("current", key)

    fun logout() {
        val key = current ?: return
        val rest = stored().filter { it.key() != key }
        save(rest)
        store.put("token|$key", null)
        store.put("id|$key", null)
        File(cacheRoot, safe(key)).deleteRecursively()
        store.put("current", rest.firstOrNull()?.key())
    }

    private suspend fun relogin() {
        val acc = account() ?: throw ApiException("Nessun account")
        val ident = acc.optString("ident").ifBlank { null }
        val (res, _) = auth(acc.getString("uid"), acc.getString("pass"), ident, pickFirst = true)
        saveSession(acc.key(), res)
    }

    private suspend fun offline() = currentCoroutineContext()[CacheOnly.Key] != null

    private suspend fun requireOnline() {
        if (offline()) throw ApiException("Dato non disponibile offline")
    }

    private suspend fun <T> authed(call: () -> Pair<Int, T>): T = withContext(Dispatchers.IO) {
        requireOnline()
        mutex.withLock { if (token() == null) relogin() }
        var r = call()
        if (r.first == 401) {
            val failedToken = token()
            mutex.withLock { if (token() == failedToken) relogin() }
            r = call()
        }
        if (r.first != 200) throw ApiException("Errore ${r.first}")
        r.second
    }

    private fun student(path: String) = "/students/${id()}$path"

    suspend fun studentId(): String = withContext(Dispatchers.IO) {
        requireOnline()
        mutex.withLock { if (token() == null) relogin() }
        id()
    }

    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9]"), "_")

    /** Le date nel percorso cambiano ogni giorno: vengono ignorate così la copia salvata resta valida. */
    private fun cacheFile(method: String, path: String): File? {
        val key = current ?: return null
        val name = safe("$method${path.replace(Regex("\\d{8}"), "D")}").take(120)
        return File(File(cacheRoot, safe(key)), "$name.json")
    }

    private suspend fun json(method: String, path: String): JSONObject {
        val file = cacheFile(method, path)
        if (offline()) {
            return withContext(Dispatchers.IO) {
                file?.takeIf { it.exists() }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
            } ?: throw ApiException("Nessun dato salvato")
        }
        val res = authed { request(method, student(path), null, token()) }
        withContext(Dispatchers.IO) { runCatching { file?.parentFile?.mkdirs(); file?.writeText(res.toString()) } }
        return res
    }

    suspend fun get(path: String) = json("GET", path)
    suspend fun post(path: String) = json("POST", path)
    suspend fun bytes(path: String) = authed { raw("GET", student(path), null, token()) }
    suspend fun postBytes(path: String) = authed { raw("POST", student(path), null, token()) }

    suspend fun webPage(url: String): String = withContext(Dispatchers.IO) {
        requireOnline()
        webSession()
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("Cookie", CookieManager.getInstance().getCookie(Endpoints.WEB).orEmpty())
        val code = c.responseCode
        val stream = if (code < 400) c.inputStream else c.errorStream
        val html = stream?.use { String(it.readBytes()) }.orEmpty()
        c.disconnect()
        if (code >= 400) throw ApiException("Errore $code durante la connessione web")
        html
    }

    suspend fun webSession() = withContext(Dispatchers.IO) {
        requireOnline()
        val acc = account() ?: return@withContext false
        val c = URI(Endpoints.WEB_LOGIN).toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
        c.outputStream.use { it.write("cid=&uid=${enc(acc.getString("uid"))}&pwd=${enc(acc.getString("pass"))}&pin=&target=".toByteArray()) }
        val code = c.responseCode
        val cookies = c.headerFields.filterKeys { it.equals("Set-Cookie", true) }.values.flatten()
        val stream = if (code < 400) c.inputStream else c.errorStream
        stream?.use { it.readBytes() }
        c.disconnect()
        val manager = CookieManager.getInstance()
        cookies.forEach { manager.setCookie(Endpoints.WEB, it) }
        manager.flush()
        code == 200 && cookies.isNotEmpty()
    }

    private fun request(method: String, path: String, body: String?, token: String?): Pair<Int, JSONObject> {
        val (code, bytes) = raw(method, path, body, token)
        return code to (runCatching { JSONObject(String(bytes)) }.getOrNull() ?: JSONObject())
    }

    private fun raw(method: String, path: String, body: String?, token: String?): Pair<Int, ByteArray> {
        val c = URI("${Endpoints.REST}$path").toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.requestMethod = method
        c.setRequestProperty("User-Agent", Endpoints.USER_AGENT)
        c.setRequestProperty("Z-Dev-Apikey", Endpoints.API_KEY)
        c.setRequestProperty("Content-Type", "application/json")
        token?.let { c.setRequestProperty("Z-Auth-Token", it) }
        body?.let { b -> c.doOutput = true; c.outputStream.use { it.write(b.toByteArray()) } }
        val code = c.responseCode
        val bytes = (if (code < 400) c.inputStream else c.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
        c.disconnect()
        return code to bytes
    }
}

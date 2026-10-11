package com.example.classevivamodernaemeglio

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

class ApiException(message: String) : Exception(message)

data class Account(val uid: String, val name: String)

private fun getPrefs(context: Context): SharedPreferences {
    return runCatching {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            "session",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse {
        runCatching { context.deleteSharedPreferences("session") }
        runCatching {
            val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                context,
                "session",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            context.getSharedPreferences("session_fallback", Context.MODE_PRIVATE)
        }
    }
}

class Api(context: Context) {
    private val prefs = getPrefs(context)
    private val mutex = Mutex()

    init {
        if (!prefs.contains("accounts") && prefs.contains("pass")) {
            val acc = JSONObject().put("uid", prefs.getString("uid", "")).put("pass", prefs.getString("pass", "")).put("name", prefs.getString("name", ""))
            prefs.edit().putString("accounts", JSONArray().put(acc).toString()).remove("pass").remove("name").apply()
        }
    }

    private fun stored(): List<JSONObject> = runCatching {
        JSONArray(prefs.getString("accounts", "[]")).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    }.getOrDefault(emptyList())

    val accounts get() = stored().map { Account(it.getString("uid"), it.getString("name")) }
    val current get() = prefs.getString("uid", null)
    val name get() = accounts.firstOrNull { it.uid == current }?.name.orEmpty()

    suspend fun login(uid: String, pass: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("ident", JSONObject.NULL).put("pass", pass).put("uid", uid)
        var r = request("POST", "/auth/login", body.toString(), null)
        if (r.second.optString("requestedAction") == "choiceIdent") {
            body.put("ident", r.second.getJSONArray("choices").getJSONObject(0).getString("ident"))
            r = request("POST", "/auth/login", body.toString(), null)
        }
        val res = r.second
        if (!res.has("token")) throw ApiException(if (r.first == 422) "Credenziali errate" else "Errore ${r.first}")
        val name = "${res.optString("firstName")} ${res.optString("lastName")}".trim()
        val list = JSONArray()
        stored().filter { it.getString("uid") != uid }.forEach { list.put(it) }
        list.put(JSONObject().put("uid", uid).put("pass", pass).put("name", name))
        prefs.edit()
            .putString("accounts", list.toString())
            .putString("uid", uid)
            .putString("token", res.getString("token"))
            .putString("id", res.optString("ident").filter { it.isDigit() })
            .apply()
    }

    fun switchTo(uid: String) = prefs.edit().putString("uid", uid).remove("token").remove("id").apply()

    fun logout() {
        val rest = stored().filter { it.getString("uid") != current }
        val list = JSONArray().also { a -> rest.forEach { a.put(it) } }
        prefs.edit().putString("accounts", list.toString()).remove("token").remove("id").apply()
        rest.firstOrNull()?.let { switchTo(it.getString("uid")) } ?: prefs.edit().remove("uid").apply()
    }

    private suspend fun relogin() {
        val uid = current ?: throw ApiException("Nessun account")
        val pass = stored().firstOrNull { it.getString("uid") == uid }?.getString("pass") ?: throw ApiException("Nessun account")
        login(uid, pass)
    }

    private suspend fun <T> authed(call: () -> Pair<Int, T>): T = withContext(Dispatchers.IO) {
        val initialToken = token()
        mutex.withLock {
            if (token() == null || (initialToken == null && prefs.getString("token", null) == null)) {
                relogin()
            }
        }
        var r = call()
        if (r.first == 401) {
            val failedToken = token()
            mutex.withLock {
                if (token() == failedToken) {
                    relogin()
                }
            }
            r = call()
        }
        if (r.first != 200) throw ApiException("Errore ${r.first}")
        r.second
    }

    private fun student(path: String) = "/students/${prefs.getString("id", "")}$path"

    suspend fun studentId(): String = withContext(Dispatchers.IO) {
        mutex.withLock { if (prefs.getString("token", null) == null) relogin() }
        prefs.getString("id", "").orEmpty()
    }
    private fun token() = prefs.getString("token", null)

    suspend fun get(path: String) = authed { request("GET", student(path), null, token()) }
    suspend fun post(path: String) = authed { request("POST", student(path), null, token()) }
    suspend fun bytes(path: String) = authed { raw("GET", student(path), null, token()) }
    suspend fun postBytes(path: String) = authed { raw("POST", student(path), null, token()) }

    suspend fun webPage(url: String): String = withContext(Dispatchers.IO) {
        webSession()
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.setRequestProperty("Cookie", CookieManager.getInstance().getCookie("https://web.spaggiari.eu").orEmpty())
        val code = c.responseCode
        val stream = if (code < 400) c.inputStream else c.errorStream
        val html = stream?.use { String(it.readBytes()) }.orEmpty()
        c.disconnect()
        if (code >= 400) throw ApiException("Errore $code durante la connessione web")
        html
    }

    suspend fun webSession() = withContext(Dispatchers.IO) {
        val uid = current ?: return@withContext false
        val pass = stored().firstOrNull { it.getString("uid") == uid }?.getString("pass") ?: return@withContext false
        val c = URI("https://web.spaggiari.eu/auth-p7/app/default/AuthApi4.php?a=aLoginPwd").toURL().openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
        c.outputStream.use { it.write("cid=&uid=${enc(uid)}&pwd=${enc(pass)}&pin=&target=".toByteArray()) }
        val code = c.responseCode
        val cookies = c.headerFields.filterKeys { it.equals("Set-Cookie", true) }.values.flatten()
        val stream = if (code < 400) c.inputStream else c.errorStream
        stream?.use { it.readBytes() }
        c.disconnect()
        val manager = CookieManager.getInstance()
        cookies.forEach { manager.setCookie("https://web.spaggiari.eu", it) }
        manager.flush()
        code == 200 && cookies.isNotEmpty()
    }

    private fun request(method: String, path: String, body: String?, token: String?): Pair<Int, JSONObject> {
        val (code, bytes) = raw(method, path, body, token)
        return code to (runCatching { JSONObject(String(bytes)) }.getOrNull() ?: JSONObject())
    }

    private fun raw(method: String, path: String, body: String?, token: String?): Pair<Int, ByteArray> {
        val c = URI("https://web.spaggiari.eu/rest/v1$path").toURL().openConnection() as HttpURLConnection
        c.requestMethod = method
        c.setRequestProperty("User-Agent", "CVVS/std/4.1.7 Android/10")
        c.setRequestProperty("Z-Dev-Apikey", "Tg1NWEwNGIgIC0K")
        c.setRequestProperty("Content-Type", "application/json")
        token?.let { c.setRequestProperty("Z-Auth-Token", it) }
        body?.let { b -> c.doOutput = true; c.outputStream.use { it.write(b.toByteArray()) } }
        val code = c.responseCode
        val bytes = (if (code < 400) c.inputStream else c.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
        c.disconnect()
        return code to bytes
    }
}

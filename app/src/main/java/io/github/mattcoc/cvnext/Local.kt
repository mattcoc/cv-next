package io.github.mattcoc.cvnext

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Chiave stabile di un compito: l'id di Classeviva, oppure data e testo se manca. */
val Event.key get() = if (id != 0L) "$id" else "${date}_${notes.hashCode()}"

/** Chiave stabile di un voto, la stessa usata dalle notifiche. */
val Grade.key get() = if (id != 0L) "$id" else "${subject}_${date}_$value"

/** Compiti segnati come fatti. Restano solo su questo telefono, separati per account. */
class DoneStore(context: Context, private val account: String?, private val onChange: () -> Unit = {}) {
    private val prefs = context.applicationContext.getSharedPreferences("done", Context.MODE_PRIVATE)
    var ids by mutableStateOf(prefs.getStringSet(account ?: "", null).orEmpty())
        private set

    fun isDone(e: Event) = e.key in ids

    fun toggle(e: Event) {
        ids = if (isDone(e)) ids - e.key else ids + e.key
        prefs.edit().putStringSet(account ?: "", ids).apply()
        onChange()
    }

    companion object {
        /** Per il widget, che non ha la Home aperta. */
        fun of(context: Context, account: String?): Set<String> =
            context.getSharedPreferences("done", Context.MODE_PRIVATE).getStringSet(account ?: "", null).orEmpty()
    }
}

/**
 * Voti già visti nell'app: quelli nuovi hanno un pallino finché non si apre la scheda Voti.
 * Al primo avvio tutti i voti presenti contano come già visti.
 */
class SeenGrades(context: Context, private val account: String?) {
    private val prefs = context.applicationContext.getSharedPreferences("seenGrades", Context.MODE_PRIVATE)
    private var seen by mutableStateOf(prefs.getStringSet(account ?: "", null))

    fun fresh(grades: List<Grade>): Set<String> {
        val s = seen ?: return emptySet()
        return grades.map { it.key }.filter { it !in s }.toSet()
    }

    fun markSeen(grades: List<Grade>) {
        val all = grades.map { it.key }.toSet()
        if (seen == all) return
        seen = all
        prefs.edit().putStringSet(account ?: "", all).apply()
    }

    /** Prima volta: nessun pallino su tutto lo storico. */
    fun start(grades: List<Grade>) {
        if (seen == null) markSeen(grades)
    }
}

/** Voti nuovi (chiavi) e compiti fatti, a disposizione delle schermate senza passarli ovunque. */
val LocalNewGrades = compositionLocalOf<Set<String>> { emptySet() }
val LocalDone = compositionLocalOf<DoneStore?> { null }

/** Numeri da mostrare sulle icone della barra: voti nuovi e circolari da leggere. */
val LocalBadges = compositionLocalOf<Map<Section, Int>> { emptyMap() }

/** Blocco dell'app con impronta, volto o codice del telefono (Android 10 e successivi). */
object AppLock {
    /** Tornando sull'app dopo questo tempo si richiede di nuovo lo sblocco. */
    const val GRACE_MS = 60_000L

    fun available(context: Context): Boolean = Build.VERSION.SDK_INT >= 29 &&
        runCatching { context.getSystemService(KeyguardManager::class.java).isDeviceSecure }.getOrDefault(false)

    fun unlock(activity: Activity, onSuccess: () -> Unit) {
        if (Build.VERSION.SDK_INT < 29) return onSuccess()
        val builder = BiometricPrompt.Builder(activity)
            .setTitle("Sblocca CV Next")
            .setSubtitle("Usa l'impronta, il volto o il codice del telefono")
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        runCatching {
            builder.build().authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            })
        }.onFailure { if (!available(activity)) onSuccess() }
    }
}

package io.github.mattcoc.cvnext

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch

/** Stato delle autorizzazioni che servono perché le notifiche arrivino ad app chiusa. */
object NotifyStatus {
    /** Falso se l'utente non ha dato il permesso (Android 13+) o ha spento le notifiche dell'app. */
    fun allowed(context: Context): Boolean = runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }.getOrDefault(true)

    /** Pagina delle notifiche di CV Next nelle impostazioni di sistema. */
    fun settings(context: Context): List<Intent> = listOfNotNull(
        if (Build.VERSION.SDK_INT >= 26) Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(SystemSettings.EXTRA_APP_PACKAGE, context.packageName) else null,
        Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    )

    fun ready(context: Context, settings: Settings) = settings.alerts.isNotEmpty() && allowed(context) && !Battery.restricted(context)
}

/** Quando riproporre l'invito: al massimo tre volte, a qualche giorno di distanza, e mai a chi ha spento le notifiche apposta. */
object Invite {
    private const val MAX = 3
    private const val PAUSE = 4L * 24 * 3600 * 1000

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun due(context: Context, settings: Settings): Boolean {
        val p = prefs(context)
        return !settings.optedOut && !NotifyStatus.ready(context, settings) &&
            p.getInt("inviteCount", 0) < MAX && System.currentTimeMillis() >= p.getLong("inviteNext", 0)
    }

    fun later(context: Context) {
        val p = prefs(context)
        p.edit().putInt("inviteCount", p.getInt("inviteCount", 0) + 1).putLong("inviteNext", System.currentTimeMillis() + PAUSE).apply()
    }
}

/** Invito ad attivare le notifiche: guida passo per passo tra permesso, risparmio batteria e avvio automatico (Xiaomi). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationInvite(settings: Settings, onClose: () -> Unit) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf(NotifyStatus.allowed(context)) }
    var restricted by remember { mutableStateOf(Battery.restricted(context)) }
    var autostart by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    // Le scelte si fanno nelle schermate di sistema: al ritorno si rilegge lo stato.
    LifecycleResumeEffect(Unit) {
        allowed = NotifyStatus.allowed(context)
        restricted = Battery.restricted(context)
        onPauseOrDispose { }
    }
    val battery = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { restricted = Battery.restricted(context) }
    val askBattery = {
        if (Battery.restricted(context)) {
            // Alcuni produttori non hanno la finestra diretta: in quel caso si apre l'elenco delle app ottimizzate.
            runCatching { battery.launch(Battery.request(context)) }.onFailure {
                runCatching { battery.launch(Intent(SystemSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            }
        }
    }
    val enable = {
        if (settings.alerts.isEmpty()) settings.toggleAll(true)
        Notifier.schedule(context, settings.alerts)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = NotifyStatus.allowed(context)
        if (granted) {
            enable()
            askBattery()
        } else denied = true
    }
    val on = settings.alerts.isNotEmpty() && allowed
    val step = when {
        !on -> 0
        restricted -> 1
        Battery.xiaomi && !autostart -> 2
        else -> 3
    }
    val close: () -> Unit = {
        if (step < 3) Invite.later(context)
        scope.launch { sheet.hide() }.invokeOnCompletion { onClose() }
    }
    val action: () -> Unit = when (step) {
        0 -> {
            {
                when {
                    allowed -> { enable(); askBattery() }
                    // Permesso già negato o notifiche spente a mano: si riattivano solo dalle impostazioni di sistema.
                    denied || Build.VERSION.SDK_INT < 33 -> { enable(); Battery.open(context, NotifyStatus.settings(context)) }
                    else -> permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        1 -> askBattery
        2 -> { { autostart = true; Battery.open(context, Battery.autostart(context)) } }
        else -> close
    }

    ModalBottomSheet(
        { if (step < 3) Invite.later(context); onClose() }, sheetState = sheet,
        containerColor = c.surfaceContainerLow.copy(alpha = 1f)
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp, 0.dp, 24.dp, 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(c.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(if (step == 3) Icons.Rounded.Check else Icons.Rounded.NotificationsActive, null, Modifier.size(36.dp), tint = c.onPrimaryContainer)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (step == 3) "Tutto pronto" else "Non perderti voti e verifiche",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (step == 3) "Ti avviseremo appena arriva qualcosa di nuovo, anche con l'app chiusa. Puoi scegliere cosa ricevere in Impostazioni → Notifiche."
                else "CV Next ti avvisa appena arriva un voto, una verifica, un compito o una circolare, anche con l'app chiusa.",
                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                InviteStep(
                    1, Icons.Rounded.NotificationsActive, "Consenti le notifiche",
                    if (denied && !allowed) "Android le ha bloccate: tocca il pulsante e attiva \"Consenti notifiche\"" else "Scegli tu cosa ricevere: voti, agenda, bacheca e quando sei tra gli interrogati",
                    done = on, current = step == 0
                )
                InviteStep(
                    2, Icons.Rounded.BatteryChargingFull, "Lascia lavorare CV Next in background",
                    "Scegli \"Consenti\": un controllo veloce all'ora, la batteria quasi non se ne accorge",
                    done = on && !restricted, current = step == 1
                )
                if (Battery.xiaomi) InviteStep(
                    3, Icons.Rounded.RestartAlt, "Attiva l'avvio automatico",
                    "Su Xiaomi, Redmi e POCO serve perché i controlli continuino quando chiudi l'app",
                    done = autostart, current = step == 2
                )
            }
            Spacer(Modifier.height(28.dp))
            Button(action, Modifier.fillMaxWidth().height(52.dp)) {
                Text(
                    when (step) {
                        0 -> if (denied || (!allowed && Build.VERSION.SDK_INT < 33)) "Apri le impostazioni" else "Attiva le notifiche"
                        1 -> "Consenti in background"
                        2 -> "Apri avvio automatico"
                        else -> "Fatto"
                    },
                    style = MaterialTheme.typography.titleSmall
                )
            }
            if (step < 3) TextButton(close, Modifier.fillMaxWidth().padding(top = 4.dp)) { Text(if (step == 0) "Non ora" else "Più tardi") }
        }
    }
}

@Composable
private fun InviteStep(number: Int, icon: ImageVector, title: String, text: String, done: Boolean, current: Boolean) {
    val c = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (done) c.primary else if (current) c.primaryContainer else c.surfaceContainerHighest, label = "step")
    val fg = if (done) c.onPrimary else if (current) c.onPrimaryContainer else c.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            if (done) Icon(Icons.Rounded.Check, "Fatto", Modifier.size(22.dp), tint = fg)
            else Icon(icon, "Passo $number", Modifier.size(22.dp), tint = fg)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                color = if (done || current) c.onSurface else c.onSurfaceVariant
            )
            Text(text, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }
    }
}

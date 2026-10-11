package com.example.classevivamodernaemeglio

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var seed by mutableIntStateOf(prefs.getInt("seed", if (Build.VERSION.SDK_INT >= 31) 0 else seeds[1].first))
        private set
    var mode by mutableIntStateOf(prefs.getInt("mode", 0))
        private set
    var alerts by mutableStateOf(runCatching {
        (prefs.getStringSet("alerts", emptySet()) ?: emptySet()).mapNotNull { n -> Alert.entries.firstOrNull { it.name == n } }.toSet()
    }.getOrDefault(emptySet()))
        private set

    fun toggle(alert: Alert, on: Boolean) {
        alerts = if (on) alerts + alert else alerts - alert
        prefs.edit().putStringSet("alerts", alerts.map { it.name }.toSet()).apply()
    }

    fun update(seed: Int = this.seed, mode: Int = this.mode) {
        this.seed = seed
        this.mode = mode
        prefs.edit().putInt("seed", seed).putInt("mode", mode).apply()
    }
}

val seeds = listOf(
    0 to "Sfondo",
    0xFF3F51B5.toInt() to "Indaco",
    0xFF1E88E5.toInt() to "Blu",
    0xFF00897B.toInt() to "Turchese",
    0xFF43A047.toInt() to "Verde",
    0xFFF9A825.toInt() to "Giallo",
    0xFFF4511E.toInt() to "Arancio",
    0xFFE53935.toInt() to "Rosso",
    0xFFD81B60.toInt() to "Rosa",
    0xFF8E24AA.toInt() to "Viola"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(settings: Settings, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Alert?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.let { if (granted) settings.toggle(it, true) }
        pending = null
        Notifier.schedule(context, settings.alerts)
    }
    val toggle: (Alert, Boolean) -> Unit = { alert, on ->
        val needs = on && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needs) {
            pending = alert
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            settings.toggle(alert, on)
            Notifier.schedule(context, settings.alerts)
        }
    }
    ModalBottomSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp, 0.dp, 24.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Impostazioni", Modifier.appear(0), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Colore", Modifier.appear(1), style = MaterialTheme.typography.titleMedium, color = c.primary)
            FlowRow(
                Modifier.fillMaxWidth().appear(2),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 5
            ) {
                seeds.filter { it.first != 0 || Build.VERSION.SDK_INT >= 31 }.forEach { (seed, label) ->
                    val sel = settings.seed == seed
                    val scale by animateFloatAsState(if (sel) 1.1f else 1f, spring(0.4f, 500f), label = "s")
                    Column(
                        Modifier.width(60.dp).clip(MaterialTheme.shapes.medium).clickable { settings.update(seed = seed) }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier.scale(scale).size(44.dp).clip(CircleShape)
                                .background(if (seed == 0) c.primaryContainer else Color(seed))
                                .border(if (sel) 3.dp else 0.dp, if (sel) c.primary else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                seed == 0 -> Icon(Icons.Rounded.Wallpaper, null, tint = if (sel) c.onPrimaryContainer else c.primary)
                                sel -> Icon(Icons.Rounded.Check, null, tint = Color.White)
                            }
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
            }
            Text("Tema", Modifier.appear(3), style = MaterialTheme.typography.titleMedium, color = c.primary)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().appear(4)) {
                listOf("Sistema", "Chiaro", "Scuro").forEachIndexed { i, label ->
                    SegmentedButton(settings.mode == i, { settings.update(mode = i) }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(label) }
                }
            }
            Text("Notifiche", Modifier.appear(5), style = MaterialTheme.typography.titleMedium, color = c.primary)
            Text(
                "L'app controlla le novità circa una volta all'ora. La prima volta registra la situazione attuale, poi ti avvisa solo delle cose nuove.",
                Modifier.appear(5), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
            )
            Card(Modifier.fillMaxWidth().appear(6)) {
                Alert.entries.forEach { alert ->
                    val on = alert in settings.alerts
                    Row(
                        Modifier.fillMaxWidth().clickable { toggle(alert, !on) }.padding(16.dp, 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(alert.title, fontWeight = FontWeight.Medium)
                            Text(alert.description, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                        }
                        Switch(on, { toggle(alert, it) })
                    }
                }
            }
            if (settings.alerts.isNotEmpty()) FilledTonalButton(
                {
                    Notifier.checkNow(context)
                    Toast.makeText(context, "Controllo avviato", Toast.LENGTH_SHORT).show()
                },
                Modifier.fillMaxWidth().appear(7)
            ) { Text("Controlla adesso") }
            Text("Aggiornamenti", Modifier.appear(8), style = MaterialTheme.typography.titleMedium, color = c.primary)
            UpdateCard(Modifier.appear(9))
        }
    }
}

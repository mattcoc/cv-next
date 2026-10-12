package io.github.mattcoc.cvnext

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Un passo della guida: [section] è la scheda da mostrare sotto, [tab] la sua posizione nella barra in basso. */
private class GuideStep(val title: String, val text: String, val icon: ImageVector, val section: Section? = null, val top: Boolean = false)

private val steps = listOf(
    GuideStep(
        "Benvenuto in CV Next", "Un giro veloce per vedere dove sono le cose. Puoi rivederlo quando vuoi dalle impostazioni.",
        Icons.Rounded.WavingHand, Section.Oggi
    ),
    GuideStep(
        "Home", "Verifiche e interrogazioni dei prossimi giorni, eventi da giustificare, pagamenti e ultimi voti. Se sei tra gli interrogati te lo dice.",
        Icons.Rounded.Home, Section.Oggi
    ),
    GuideStep(
        "Giornata", "Lezioni, compiti, voti e assenze giorno per giorno. Scorri i giorni in alto e aggiungi i tuoi promemoria.",
        Icons.Rounded.Today, Section.Giornata
    ),
    GuideStep(
        "Voti", "Medie per materia e per periodo. Tocca una materia per il grafico e per sapere che voto ti serve per arrivare alla media che vuoi.",
        Icons.Rounded.Grade, Section.Voti
    ),
    GuideStep(
        "Bacheca", "Circolari e comunicazioni della scuola, con gli allegati da aprire direttamente nell'app.",
        Icons.Rounded.Campaign, Section.Bacheca
    ),
    GuideStep(
        "Funzioni", "Agenda completa, assenze, note, lezioni, file dei docenti, scrutini e PagOnline.",
        Icons.Rounded.Apps, Section.Funzioni
    ),
    GuideStep(
        "Impostazioni e profilo", "In alto a destra: stile grafico, tema, notifiche e promemoria della sera. Toccando la tua iniziale cambi o aggiungi un account.",
        Icons.Rounded.Settings, Section.Oggi, top = true
    )
)

/** Guida a fumetti mostrata al primo avvio: ogni passo apre la scheda di cui parla e la indica nella barra. */
@Composable
fun GuideOverlay(onSection: (Section) -> Unit, start: Int = 0, onDone: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(start) }
    val step = steps[index]
    LaunchedEffect(index) { step.section?.let(onSection) }
    BackHandler { if (index > 0) index-- else onDone() }
    val bar = Section.entries.filter { it.inBar }
    val c = MaterialTheme.colorScheme
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).pointerInput(Unit) { detectTapGestures { } }
    ) {
        val tab = if (index == 0 || step.top) -1 else bar.indexOf(step.section)
        // Centro del pulsante nella barra: margine 16 dp, bordo interno 6 dp, pulsanti larghi uguali
        val inner = maxWidth - 44.dp
        val arrowX = if (step.top) maxWidth - 76.dp else 22.dp + inner * ((tab + 0.5f) / bar.size)
        val cardModifier = Modifier.padding(horizontal = 20.dp).widthIn(max = 420.dp).fillMaxWidth()
        when {
            step.top -> Column(Modifier.fillMaxWidth().padding(top = topInset + 58.dp)) {
                Arrow(arrowX)
                GuideCard(step, index, cardModifier.align(Alignment.CenterHorizontally), { index++ }, onDone)
            }
            tab >= 0 -> Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = bottomInset + 86.dp)) {
                GuideCard(step, index, cardModifier.align(Alignment.CenterHorizontally), { index++ }, onDone)
                Arrow(arrowX, down = true)
            }
            else -> GuideCard(step, index, cardModifier.align(Alignment.Center), { index++ }, onDone)
        }
    }
}

@Composable
private fun Arrow(x: androidx.compose.ui.unit.Dp, down: Boolean = false) = Box(
    Modifier.offset(x = x - 9.dp, y = if (down) (-9).dp else 9.dp).size(18.dp).rotate(45f)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 1f))
)

@Composable
private fun GuideCard(step: GuideStep, index: Int, modifier: Modifier, onNext: () -> Unit, onDone: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val last = index == steps.lastIndex
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = c.surfaceContainerHigh.copy(alpha = 1f), shadowElevation = 12.dp) {
        AnimatedContent(step, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "guide") { s ->
            Column(Modifier.padding(20.dp, 20.dp, 12.dp, 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(c.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(s.icon, null, tint = c.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("${steps.indexOf(s) + 1} di ${steps.size}", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                        Text(s.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(s.text, Modifier.padding(top = 12.dp, end = 8.dp), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    // Puntini di avanzamento
                    Row(Modifier.weight(1f).padding(start = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        steps.indices.forEach { i ->
                            Box(Modifier.size(if (i == index) 8.dp else 6.dp).clip(CircleShape).background(if (i == index) c.primary else c.outlineVariant))
                        }
                    }
                    if (!last) TextButton(onDone) { Text("Salta") }
                    Spacer(Modifier.width(4.dp))
                    Button(if (last) onDone else onNext) { Text(if (last) "Inizia" else "Avanti") }
                }
            }
        }
    }
}

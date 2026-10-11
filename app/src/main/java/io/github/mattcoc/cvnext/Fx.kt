package io.github.mattcoc.cvnext

import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

fun Modifier.appear(index: Int) = composed {
    // Con le animazioni disattivate nelle impostazioni del telefono gli elementi compaiono subito.
    val reduced = LocalContext.current.let {
        android.provider.Settings.Global.getFloat(it.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    var done by rememberSaveable { mutableStateOf(index > 12 || reduced) }
    val a = remember { Animatable(if (done) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (done) return@LaunchedEffect
        delay(index * 35L)
        a.animateTo(1f, tween(380, easing = EmphasizedDecelerate))
        done = true
    }
    graphicsLayer {
        alpha = a.value
        translationY = (1 - a.value) * 48.dp.toPx()
    }
}

val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

fun Modifier.fadeEdges() = graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
    drawContent()
    drawRect(
        Brush.horizontalGradient(0f to Color.Transparent, 0.06f to Color.Black, 0.94f to Color.Black, 1f to Color.Transparent),
        blendMode = BlendMode.DstIn
    )
}

@Composable
fun animatedValue(target: Double?): Float {
    val a = remember { Animatable(0f) }
    LaunchedEffect(target) { a.animateTo((target ?: 0.0).toFloat(), tween(1000, easing = FastOutSlowInEasing)) }
    return a.value
}

@Composable
fun Avatar(name: String, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Box(modifier.size(size).clip(CircleShape).background(c.primaryContainer), contentAlignment = Alignment.Center) {
        Text(
            name.trim().take(1).uppercase().ifBlank { "?" }, color = c.onPrimaryContainer, fontWeight = FontWeight.Bold,
            style = if (size > 40.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
fun ExpandableText(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, lines: Int = 3) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var overflow by remember(text) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "arrow")
    Column(Modifier.animateContentSize(spring(0.9f, 400f))) {
        Text(
            text, color = color, maxLines = if (expanded) Int.MAX_VALUE else lines, overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow }
        )
        if (overflow || expanded) Row(
            Modifier.padding(top = 4.dp).clip(CircleShape).clickable { expanded = !expanded }.padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (expanded) "Mostra meno" else "Leggi tutto", color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold
            )
            Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(20.dp).rotate(rotation), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun FloatingNavBar(selected: Section, modifier: Modifier = Modifier, onSelect: (Section) -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = c.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 10.dp
    ) {
        Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Section.entries.filter { it.inBar }.forEach { s ->
                val sel = s == selected
                val pill by animateFloatAsState(if (sel) 1f else 0f, spring(0.75f, 600f), label = "pill")
                val fg by animateColorAsState(if (sel) c.onSecondaryContainer else c.onSurfaceVariant, label = "fg")
                val source = remember { MutableInteractionSource() }
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable(source, null) { onSelect(s) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(Modifier.size(56.dp, 32.dp).clip(CircleShape).indication(source, ripple()), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.matchParentSize().graphicsLayer { scaleX = 0.4f + 0.6f * pill; alpha = pill }
                                .clip(CircleShape).background(c.secondaryContainer)
                        )
                        Crossfade(sel, label = "icon") { Icon(if (it) s.selected else s.icon, s.label, tint = fg) }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false,
                        color = if (sel) c.onSurface else c.onSurfaceVariant,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

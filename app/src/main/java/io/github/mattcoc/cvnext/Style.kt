package io.github.mattcoc.cvnext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.rememberDynamicColorScheme

/** Stile grafico dell'app. Quelli con [ownColors] hanno i loro colori e ignorano il colore scelto. */
enum class AppStyle(val label: String, val description: String, val ownColors: Boolean) {
    Material("Material You", "Lo stile di Android, con il colore che scegli", false),
    Glass("Liquid Glass", "Superfici trasparenti su uno sfondo colorato", false),
    OneUI("One UI", "Come sui Samsung Galaxy", true),
    HyperOS("HyperOS", "Come sugli Xiaomi", true),
    Nothing("Nothing OS", "Bianco e nero, scritte a puntini e rosso", true)
}

val LocalStyle = staticCompositionLocalOf { AppStyle.Material }
val LocalDark = staticCompositionLocalOf { false }

/** Tablet, schermi larghi o telefono in orizzontale: barra laterale e contenuto centrato. */
val LocalWide = staticCompositionLocalOf { false }

/** Margine laterale che tiene il contenuto in una colonna leggibile sugli schermi larghi. */
fun sideMargin(width: Dp, content: Dp = 760.dp): Dp = maxOf(16.dp, (width - content) / 2)

/** Colori dello stile: parte da quelli di Material You e cambia sfondi, superfici e accento. */
@Composable
fun styledColors(style: AppStyle, base: ColorScheme, dark: Boolean): ColorScheme = when (style) {
    AppStyle.Material -> base
    AppStyle.Glass -> base.copy(
        background = Color.Transparent,
        surface = Color.Transparent,
        surfaceContainerHighest = if (dark) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.55f)
    )
    AppStyle.OneUI -> brand(Color(0xFF0381FE), dark).copy(
        primary = if (dark) Color(0xFF3E91FF) else Color(0xFF0072DE),
        background = if (dark) Color.Black else Color(0xFFF4F4F6),
        surface = if (dark) Color.Black else Color(0xFFF4F4F6),
        surfaceContainerHighest = if (dark) Color(0xFF171717) else Color.White,
        surfaceContainerLow = if (dark) Color(0xFF171717) else Color.White,
        surfaceContainer = if (dark) Color(0xFF1F1F1F) else Color.White,
        surfaceContainerHigh = if (dark) Color(0xFF262626) else Color(0xFFF0F0F2)
    )
    AppStyle.HyperOS -> brand(Color(0xFF3482FF), dark).copy(
        primary = if (dark) Color(0xFF4D8FFF) else Color(0xFF0D84FF),
        background = if (dark) Color.Black else Color(0xFFF7F7F7),
        surface = if (dark) Color.Black else Color(0xFFF7F7F7),
        surfaceContainerHighest = if (dark) Color(0xFF1C1C1E) else Color.White,
        surfaceContainerLow = if (dark) Color(0xFF1C1C1E) else Color.White,
        surfaceContainer = if (dark) Color(0xFF242426) else Color.White,
        surfaceContainerHigh = if (dark) Color(0xFF2C2C2E) else Color(0xFFF2F2F2)
    )
    AppStyle.Nothing -> {
        val ink = if (dark) Color.White else Color.Black
        val grey = if (dark) Color(0xFF262626) else Color(0xFFE6E6E6)
        brand(Color(0xFF888888), dark).copy(
            primary = Color(0xFFD71921), onPrimary = Color.White,
            primaryContainer = grey, onPrimaryContainer = ink,
            secondary = if (dark) Color(0xFFBDBDBD) else Color(0xFF5C5C5C),
            secondaryContainer = grey, onSecondaryContainer = ink,
            tertiary = Color(0xFFD71921),
            tertiaryContainer = grey, onTertiaryContainer = ink,
            background = if (dark) Color.Black else Color(0xFFF2F2F2),
            surface = if (dark) Color.Black else Color(0xFFF2F2F2),
            onSurface = ink, onBackground = ink,
            onSurfaceVariant = if (dark) Color(0xFF9E9E9E) else Color(0xFF666666),
            surfaceContainerHighest = if (dark) Color(0xFF141414) else Color.White,
            surfaceContainerLow = if (dark) Color(0xFF141414) else Color.White,
            surfaceContainer = if (dark) Color(0xFF1B1B1B) else Color.White,
            surfaceContainerHigh = if (dark) Color(0xFF1F1F1F) else Color(0xFFEDEDED),
            outlineVariant = if (dark) Color(0xFF333333) else Color(0xFFD6D6D6)
        )
    }
}

@Composable
private fun brand(seed: Color, dark: Boolean) = rememberDynamicColorScheme(seed, dark, false)

fun styledShapes(style: AppStyle): Shapes = when (style) {
    AppStyle.Material -> shapes(8, 12, 20, 28, 32)
    AppStyle.Glass -> shapes(10, 16, 26, 30, 36)
    AppStyle.OneUI -> shapes(10, 16, 26, 28, 32)
    AppStyle.HyperOS -> shapes(8, 12, 16, 20, 28)
    AppStyle.Nothing -> shapes(6, 12, 18, 22, 28)
}

private fun shapes(xs: Int, s: Int, m: Int, l: Int, xl: Int) = Shapes(
    extraSmall = RoundedCornerShape(xs.dp), small = RoundedCornerShape(s.dp), medium = RoundedCornerShape(m.dp),
    large = RoundedCornerShape(l.dp), extraLarge = RoundedCornerShape(xl.dp)
)

/** Doto: carattere a puntini simile a quello di Nothing (licenza OFL, vedi licenses/Doto-OFL.txt), fissato a peso 900 e puntini tondi. */
private val Dots = FontFamily(Font(R.font.doto))

fun styledTypography(style: AppStyle): Typography {
    val t = Typography()
    return when (style) {
        AppStyle.Nothing -> t.copy(
            displayLarge = t.displayLarge.copy(fontFamily = Dots), displayMedium = t.displayMedium.copy(fontFamily = Dots),
            displaySmall = t.displaySmall.copy(fontFamily = Dots),
            headlineLarge = t.headlineLarge.copy(fontFamily = Dots), headlineMedium = t.headlineMedium.copy(fontFamily = Dots),
            headlineSmall = t.headlineSmall.copy(fontFamily = Dots), titleLarge = t.titleLarge.copy(fontFamily = Dots),
            titleMedium = t.titleMedium.copy(fontFamily = FontFamily.Monospace),
            titleSmall = t.titleSmall.copy(fontFamily = FontFamily.Monospace),
            labelLarge = t.labelLarge.copy(fontFamily = FontFamily.Monospace),
            labelMedium = t.labelMedium.copy(fontFamily = FontFamily.Monospace),
            labelSmall = t.labelSmall.copy(fontFamily = FontFamily.Monospace)
        )
        // One UI: titoli grandi, sezioni piccole e grigie
        AppStyle.OneUI -> t.copy(
            headlineMedium = t.headlineMedium.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
            headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
            titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = t.titleMedium.copy(fontSize = 14.sp)
        )
        AppStyle.HyperOS -> t.copy(
            headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
            headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
            titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        else -> t
    }
}

/** Bordo sottile delle superfici di vetro. */
@Composable
fun styleBorder(): BorderStroke? = when (LocalStyle.current) {
    AppStyle.Glass -> BorderStroke(1.dp, Color.White.copy(alpha = if (LocalDark.current) 0.14f else 0.6f))
    AppStyle.Nothing -> if (LocalDark.current) BorderStroke(1.dp, Color(0xFF262626)) else null
    else -> null
}

/** Colore dei pannelli e della barra in basso. */
@Composable
fun panelColor(): Color = MaterialTheme.colorScheme.let { c ->
    when (LocalStyle.current) {
        AppStyle.Material -> c.surfaceContainerLow
        AppStyle.Glass -> if (LocalDark.current) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.55f)
        else -> c.surfaceContainerHighest
    }
}

@Composable
fun barColor(): Color = MaterialTheme.colorScheme.let { c ->
    when (LocalStyle.current) {
        AppStyle.Material -> c.surfaceContainer
        AppStyle.Glass -> if (LocalDark.current) Color(0xFF1C1C24).copy(alpha = 0.95f) else Color.White.copy(alpha = 0.96f)
        else -> c.surfaceContainer
    }
}

fun barElevation(style: AppStyle): Dp = when (style) {
    AppStyle.Nothing -> 0.dp
    AppStyle.Glass -> 6.dp
    else -> 10.dp
}

/** Sfondo delle schermate: per Liquid Glass un fondo colorato, che si vede attraverso le superfici. */
@Composable
fun Backdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    if (LocalStyle.current != AppStyle.Glass) {
        Surface(modifier) { Box(content = content) }
        return
    }
    val c = MaterialTheme.colorScheme
    val dark = LocalDark.current
    val base = if (dark) Color(0xFF0C0C12) else Color(0xFFF1F2F8)
    val a = if (dark) 0.42f else 0.5f
    Box(
        modifier.background(base).drawBehind {
            val r = size.maxDimension
            drawCircle(Brush.radialGradient(listOf(c.primary.copy(alpha = a), Color.Transparent), Offset(0f, size.height * 0.08f), r * 0.6f), r * 0.6f, Offset(0f, size.height * 0.08f))
            drawCircle(Brush.radialGradient(listOf(c.tertiary.copy(alpha = a * 0.8f), Color.Transparent), Offset(size.width, size.height * 0.45f), r * 0.55f), r * 0.55f, Offset(size.width, size.height * 0.45f))
            drawCircle(Brush.radialGradient(listOf(c.secondary.copy(alpha = a * 0.7f), Color.Transparent), Offset(size.width * 0.2f, size.height), r * 0.6f), r * 0.6f, Offset(size.width * 0.2f, size.height))
        }
    ) { CompositionLocalProvider(LocalContentColor provides c.onSurface) { content() } }
}

/** Card di Material 3 con forma, bordo e ombra dello stile scelto. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) = androidx.compose.material3.Card(modifier, shape, colors, elevation, border ?: styleBorder(), content)

@Composable
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) = androidx.compose.material3.Card(onClick, modifier, enabled, shape, colors, elevation, border ?: styleBorder(), null, content)

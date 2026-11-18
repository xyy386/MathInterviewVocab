package com.xingyiyao.mathinterviewvocab.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

data class AcademicPalette(
    val isDark: Boolean,
    val ink: Color,
    val blue: Color,
    val accent: Color,
    val paper: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val grid: Color,
    val success: Color,
    val error: Color,
    val muted: Color,
)

private val LightAcademicPalette = AcademicPalette(
    isDark = false,
    ink = Color(0xFF14213D),
    blue = Color(0xFF2855A6),
    accent = Color(0xFF3E72D6),
    paper = Color(0xFFF5F8FD),
    surface = Color(0xFFFFFFFF),
    surfaceSubtle = Color(0xFFEDF2FA),
    grid = Color(0xFFD7E2F0),
    success = Color(0xFF18794E),
    error = Color(0xFFB83A3A),
    muted = Color(0xFF56657D),
)

private val DarkAcademicPalette = AcademicPalette(
    isDark = true,
    ink = Color(0xFFEBF1FA),
    blue = Color(0xFF70A4FF),
    accent = Color(0xFF93BCFF),
    paper = Color(0xFF0C1424),
    surface = Color(0xFF152036),
    surfaceSubtle = Color(0xFF1D2B47),
    grid = Color(0xFF28395B),
    success = Color(0xFF42D68C),
    error = Color(0xFFFF7070),
    muted = Color(0xFF98A8C2),
)

val LocalAcademicPalette = staticCompositionLocalOf { LightAcademicPalette }

object Academic {
    val Ink: Color
        @Composable @ReadOnlyComposable get() = LocalAcademicPalette.current.ink
    val Blue: Color
        @Composable @ReadOnlyComposable get() = LocalAcademicPalette.current.blue
    val Accent: Color
        @Composable @ReadOnlyComposable get() = LocalAcademicPalette.current.accent
    val Paper: Color
        @Composable @ReadOnlyComposable get() = LocalAcademicPalette.current.paper
    val Surface: Color
        @Composable @ReadOnlyComposable get() = LocalAcademicPalette.current.surface
    val SurfaceSubtle: Color
        @Composable @ReadOnlyComposable get() = LocalAcademicPalette.current.surfaceSubtle
    val SecondarySurface: Color
        @Composable @ReadOnlyComposable get() = LocalAcademicPalette.current.surfaceSubtle
    val BlueSoft: Color
        @Composable @ReadOnlyComposable get() = LocalAcademicPalette.current.blue.copy(
            alpha = if (LocalAcademicPalette.current.isDark) 0.22f else 0.10f,
        )
    val Amber: Color
        @Composable @ReadOnlyComposable get() = if (LocalAcademicPalette.current.isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
    val Grid: Color
        @Composable @ReadOnlyComposable get() = LocalAcademicPalette.current.grid
    val Success: Color
        @Composable @ReadOnlyComposable get() = LocalAcademicPalette.current.success
    val Error: Color
        @Composable @ReadOnlyComposable get() = LocalAcademicPalette.current.error
    val Muted: Color
        @Composable @ReadOnlyComposable get() = LocalAcademicPalette.current.muted
    val IsDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalAcademicPalette.current.isDark

    val Shape = RoundedCornerShape(18.dp)

    @Composable
    @ReadOnlyComposable
    fun subjectAccent(@Suppress("UNUSED_PARAMETER") subjectID: String?): Color = Blue
}

@Composable
fun AcademicTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkAcademicPalette else LightAcademicPalette
    val typography = Typography(
        headlineLarge = Typography().headlineLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
        headlineMedium = Typography().headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
        titleLarge = Typography().titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
        titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.blue,
            onPrimary = Color(0xFF091224),
            primaryContainer = Color(0xFF22365E),
            onPrimaryContainer = palette.ink,
            secondary = palette.accent,
            background = palette.paper,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.surfaceSubtle,
            onSurfaceVariant = palette.muted,
            outline = palette.grid,
            error = palette.error,
        )
    } else {
        lightColorScheme(
            primary = palette.blue,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE6EDFA),
            onPrimaryContainer = palette.ink,
            secondary = palette.ink,
            background = palette.paper,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.surfaceSubtle,
            onSurfaceVariant = palette.muted,
            outline = palette.grid,
            error = palette.error,
        )
    }
    CompositionLocalProvider(LocalAcademicPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}

@Composable
fun AcademicCard(
    modifier: Modifier = Modifier,
    padding: Dp = 22.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = Academic.Shape,
        border = BorderStroke(1.dp, Academic.Grid),
        elevation = CardDefaults.cardElevation(defaultElevation = if (Academic.IsDark) 0.dp else 2.dp),
        colors = CardDefaults.cardColors(containerColor = Academic.Surface),
    ) {
        Column(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
fun ReadingColumn(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 820.dp,
    resetKey: Any? = Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val currentKey = resetKey?.toString().orEmpty()
    var previousKey by rememberSaveable { mutableStateOf(currentKey) }
    LaunchedEffect(currentKey) {
        if (previousKey != currentKey) {
            previousKey = currentKey
            scrollState.scrollTo(0)
        }
    }
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(scrollState).padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = maxWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}

@Composable
fun SectionHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Academic.Ink)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Academic.Muted)
    }
}

@Composable
fun TermBadge(text: String, color: Color = Academic.Blue) {
    Surface(
        color = color.copy(alpha = if (Academic.IsDark) 0.16f else 0.10f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.28f)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun Metric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            value,
            color = Academic.Blue,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
        )
        Text(label, color = Academic.Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * 6. 进度回弹：完成步骤后进度线先超调再小幅回弹，强化完成确认感。
 */
@Composable
fun MasteryLine(fraction: Double) {
    val target = fraction.toFloat().coerceIn(0f, 1f)
    val animated = remember { Animatable(target) }

    LaunchedEffect(target) {
        val current = animated.value
        val delta = target - current
        if (abs(delta) > 0.005f) {
            if (delta > 0f) {
                val overshoot = (target + (delta * 0.35f).coerceIn(0.03f, 0.065f)).coerceAtMost(1.04f)
                animated.animateTo(overshoot, tween(135, easing = FastOutSlowInEasing))
            }
            animated.animateTo(
                targetValue = target,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = 320f),
            )
        }
    }

    val trackColor = Academic.Grid
    val primaryColor = Academic.Blue
    val accentColor = Academic.Accent

    Canvas(
        modifier = Modifier.fillMaxWidth().height(10.dp),
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = radius,
        )
        val rawFill = (size.width * animated.value).coerceIn(0f, size.width)
        if (rawFill > 1f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(primaryColor, accentColor)),
                size = Size(rawFill, size.height),
                cornerRadius = radius,
            )
            val tipX = rawFill.coerceIn(size.height / 2f, size.width - size.height / 2f)
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = size.height * 0.26f,
                center = Offset(tipX, size.height / 2f),
            )
        }
    }
}

val PagePadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp)

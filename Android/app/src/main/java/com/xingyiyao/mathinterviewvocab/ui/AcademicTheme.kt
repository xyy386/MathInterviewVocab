package com.xingyiyao.mathinterviewvocab.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Academic {
    val Ink = Color(0xFF14213D)
    val Blue = Color(0xFF2E5AAC)
    val Paper = Color(0xFFF7F9FC)
    val Grid = Color(0xFFD9E4F2)
    val Success = Color(0xFF1C7C54)
    val Error = Color(0xFFB54343)
    val Muted = Color(0xFF56657D)
    val Shape = RoundedCornerShape(18.dp)
}

@Composable
fun AcademicTheme(content: @Composable () -> Unit) {
    val typography = Typography(
        headlineLarge = Typography().headlineLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
        headlineMedium = Typography().headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
        titleLarge = Typography().titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
        titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Academic.Blue, onPrimary = Color.White,
            primaryContainer = Color(0xFFE6EDFA), onPrimaryContainer = Academic.Ink,
            secondary = Academic.Ink, background = Academic.Paper,
            surface = Color.White, onSurface = Academic.Ink,
            surfaceVariant = Color(0xFFEDF2F9), onSurfaceVariant = Academic.Muted,
            outline = Academic.Grid, error = Academic.Error,
        ),
        typography = typography,
        content = content,
    )
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
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
    Surface(color = color.copy(alpha = 0.09f), shape = RoundedCornerShape(7.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun Metric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, color = Academic.Blue, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, fontSize = 28.sp)
        Text(label, color = Academic.Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun MasteryLine(fraction: Double) {
    LinearProgressIndicator(
        progress = { fraction.toFloat().coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth(),
        color = Academic.Blue,
        trackColor = Academic.Grid,
    )
}

val PagePadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp)

package com.pitchkeeper.tuner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Fog = Color(0xFFE9F0F2)
val Paper = Color(0xFFF5F8F7)
val Ink = Color(0xFF173C45)
val SecondaryInk = Color(0xFF607A80)
val SeaGlass = Color(0xFF277F79)
val SeaGlassLight = Color(0xFFB7DCD4)
val Coral = Color(0xFFC6634E)
val Line = Color(0xFFB5C8CD)

private val PitchColors = lightColorScheme(
    primary = SeaGlass,
    onPrimary = Paper,
    background = Fog,
    onBackground = Ink,
    surface = Fog,
    onSurface = Ink,
    surfaceVariant = Paper,
    onSurfaceVariant = SecondaryInk,
    outline = Line,
    error = Coral,
    onError = Paper,
)

@Composable
fun TunerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PitchColors, content = content)
}

package com.mobilaunch.weave.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * One fixed palette drives the whole app – the 3D web, the world editor, and every button,
 * chip and dialog on top of them. Earlier builds let the chrome follow Android's per-wallpaper
 * Material You colours while the web painted its own fixed "Twilight" gradient underneath;
 * on plenty of wallpapers those two systems visibly clashed (a muddy FAB over a bright violet
 * sky, a differently-tinted chip in the editor than on the main screen). Keeping everything on
 * this one palette is what makes the UI consistent screen to screen and device to device.
 */
object Twilight {
    val top = Color(0xFF232A72)
    val bottom = Color(0xFF4B2C73)
    val nebulas = listOf(Color(0x48FF7EB6), Color(0x4060E3E0), Color(0x34FFD166))
    val label = Color(0xFF1D1B3A)
    val labelPill = Color(0xEEFFFFFF)
    val orbs = listOf(Color(0xFF8AB4F8), Color(0xFFFF8BCB), Color(0xFF78D9EC), Color(0xFFFDD663))
    val focus = Color(0xFFFFE08A)

    /** The frosted-glass fill every "resting" chip, bubble and field shares. */
    val glass = Color.White.copy(alpha = 0.14f)
}

private val WeaveColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0B1550),
    primaryContainer = Color(0xFF1F3B8C),
    onPrimaryContainer = Color(0xFFD8E6FF),
    secondary = Color(0xFF8DE0EC),
    onSecondary = Color(0xFF00363D),
    secondaryContainer = Twilight.glass,
    onSecondaryContainer = Color(0xFFF3FBFC),
    tertiary = Color(0xFFFFB0DE),
    onTertiary = Color(0xFF4A0032),
    tertiaryContainer = Color(0xFF6B2452),
    onTertiaryContainer = Color(0xFFFFE0F0),
    background = Twilight.bottom,
    onBackground = Color(0xFFF6F3FF),
    surface = Color(0xFF2B2660),
    onSurface = Color(0xFFF6F3FF),
    surfaceVariant = Color(0xFF433D80),
    onSurfaceVariant = Color(0xFFCCC6EC),
    surfaceContainerLowest = Color(0xFF1C1852),
    surfaceContainerLow = Color(0xFF241F5C),
    surfaceContainer = Color(0xFF2B2666),
    surfaceContainerHigh = Color(0xFF362F76),
    surfaceContainerHighest = Color(0xFF413A86),
    outline = Color.White.copy(alpha = 0.35f),
    outlineVariant = Color.White.copy(alpha = 0.18f),
    error = Color(0xFFFF7A7A),
    onError = Color(0xFF400000),
    errorContainer = Color(0xFF5C1F1F),
    onErrorContainer = Color(0xFFFFD9D9),
)

/** Always dark and always this same Twilight palette – thoughts glow best against the night. */
@Composable
fun WeaveTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WeaveColorScheme, content = content)
}

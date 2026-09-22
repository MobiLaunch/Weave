package com.mobilaunch.weave.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Google-flavoured fallback for devices without Material You (Android 11 and older).
private val WeaveDark = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFF7FCFFF),
    onSecondary = Color(0xFF003549),
    secondaryContainer = Color(0xFF004A77),
    onSecondaryContainer = Color(0xFFC2E7FF),
    tertiary = Color(0xFF6DD58C),
    onTertiary = Color(0xFF0A3818),
    tertiaryContainer = Color(0xFF0F5223),
    onTertiaryContainer = Color(0xFFC4EED0),
    background = Color(0xFF0F1014),
    onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF0F1014),
    onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF444746),
    onSurfaceVariant = Color(0xFFC4C7C5),
    surfaceContainerLowest = Color(0xFF0B0C0F),
    surfaceContainerLow = Color(0xFF17181C),
    surfaceContainer = Color(0xFF1B1C20),
    surfaceContainerHigh = Color(0xFF26272B),
    surfaceContainerHighest = Color(0xFF313236),
    outline = Color(0xFF8E918F),
    error = Color(0xFFF2B8B5),
)

/** Always dark – thoughts glow best against the night – and tinted by the wallpaper on Pixel. */
@Composable
fun WeaveTheme(content: @Composable () -> Unit) {
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        WeaveDark
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

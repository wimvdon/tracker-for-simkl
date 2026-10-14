package io.github.impca201.trackerforsimkl.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.impca201.trackerforsimkl.data.ThemeMode

// Fallback palette (no dynamic color, or dynamic color turned off): the violet of the app icon,
// with the icon's magenta as the tertiary accent. Material 3 tonal values from seed #7A5CFF.
private val LightColors =
    lightColorScheme(
        primary = Color(0xFF6146D9),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE6DEFF),
        onPrimaryContainer = Color(0xFF1D0061),
        secondary = Color(0xFF5F5C71),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE5DFF9),
        onSecondaryContainer = Color(0xFF1C192C),
        tertiary = Color(0xFF8E3A9A),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFD6FB),
        onTertiaryContainer = Color(0xFF36003D),
        background = Color(0xFFFDF8FF),
        onBackground = Color(0xFF1C1B20),
        surface = Color(0xFFFDF8FF),
        onSurface = Color(0xFF1C1B20),
        surfaceVariant = Color(0xFFE5E0EC),
        onSurfaceVariant = Color(0xFF48454E),
        outline = Color(0xFF79757F),
        outlineVariant = Color(0xFFC9C4D0),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF7F2FA),
        surfaceContainer = Color(0xFFF1ECF4),
        surfaceContainerHigh = Color(0xFFEBE6EE),
        surfaceContainerHighest = Color(0xFFE6E1E9),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFC9BEFF),
        onPrimary = Color(0xFF2F0E99),
        primaryContainer = Color(0xFF4828BF),
        onPrimaryContainer = Color(0xFFE6DEFF),
        secondary = Color(0xFFC8C3DC),
        onSecondary = Color(0xFF312E41),
        secondaryContainer = Color(0xFF474459),
        onSecondaryContainer = Color(0xFFE5DFF9),
        tertiary = Color(0xFFF4AEF7),
        onTertiary = Color(0xFF55105F),
        tertiaryContainer = Color(0xFF712577),
        onTertiaryContainer = Color(0xFFFFD6FB),
        background = Color(0xFF141318),
        onBackground = Color(0xFFE6E1E9),
        surface = Color(0xFF141318),
        onSurface = Color(0xFFE6E1E9),
        surfaceVariant = Color(0xFF48454E),
        onSurfaceVariant = Color(0xFFC9C4D0),
        outline = Color(0xFF938F99),
        outlineVariant = Color(0xFF48454E),
        surfaceContainerLowest = Color(0xFF0F0D13),
        surfaceContainerLow = Color(0xFF1C1B20),
        surfaceContainer = Color(0xFF201F24),
        surfaceContainerHigh = Color(0xFF2B292F),
        surfaceContainerHighest = Color(0xFF36343A),
    )

@Composable
fun TrackerTheme(
    mode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark =
        when (mode) {
            ThemeMode.System -> isSystemInDarkTheme()
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }
    val context = LocalContext.current
    val colors =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dark -> DarkColors
            else -> LightColors
        }
    SystemBarsFollowTheme(dark)
    MaterialTheme(colorScheme = colors, content = content)
}

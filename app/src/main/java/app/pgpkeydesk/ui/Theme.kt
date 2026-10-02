package app.pgpkeydesk.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
class StatusColors(val ok: Color, val warn: Color, val error: Color, val subtle: Color)

val LocalStatusColors = staticCompositionLocalOf { StatusColors(Color.Green, Color.Yellow, Color.Red, Color.Gray) }

// Same Catppuccin palette as the Windows app (Mocha dark / Latte light); every surface role is set explicitly
// so no default Material purple/pink tint leaks in.
private val Dark = darkColorScheme(
    primary = Color(0xFF89B4FA), onPrimary = Color(0xFF11111B),
    primaryContainer = Color(0xFF313244), onPrimaryContainer = Color(0xFFCDD6F4),
    secondary = Color(0xFF89DCEB), onSecondary = Color(0xFF11111B),
    secondaryContainer = Color(0xFF45475A), onSecondaryContainer = Color(0xFFCDD6F4),
    tertiary = Color(0xFFA6E3A1), onTertiary = Color(0xFF11111B),
    tertiaryContainer = Color(0xFF313244), onTertiaryContainer = Color(0xFFA6E3A1),
    error = Color(0xFFF38BA8), onError = Color(0xFF11111B),
    errorContainer = Color(0xFF3D1C26), onErrorContainer = Color(0xFFF38BA8),
    background = Color(0xFF1E1E2E), onBackground = Color(0xFFCDD6F4),
    surface = Color(0xFF1E1E2E), onSurface = Color(0xFFCDD6F4),
    surfaceVariant = Color(0xFF313244), onSurfaceVariant = Color(0xFFA6ADC8),
    surfaceTint = Color(0xFF89B4FA),
    surfaceContainerLowest = Color(0xFF11111B), surfaceContainerLow = Color(0xFF181825),
    surfaceContainer = Color(0xFF181825), surfaceContainerHigh = Color(0xFF313244), surfaceContainerHighest = Color(0xFF45475A),
    surfaceBright = Color(0xFF45475A), surfaceDim = Color(0xFF11111B),
    inverseSurface = Color(0xFFCDD6F4), inverseOnSurface = Color(0xFF1E1E2E), inversePrimary = Color(0xFF1E66F5),
    outline = Color(0xFF6C7086), outlineVariant = Color(0xFF45475A), scrim = Color(0xFF000000)
)

private val Light = lightColorScheme(
    primary = Color(0xFF1E66F5), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E2FD), onPrimaryContainer = Color(0xFF14346F),
    secondary = Color(0xFF04A5E5), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E2FD), onSecondaryContainer = Color(0xFF14346F),
    tertiary = Color(0xFF40A02B), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDF0D8), onTertiaryContainer = Color(0xFF1B4A10),
    error = Color(0xFFD20F39), onError = Color.White,
    errorContainer = Color(0xFFF9D9DF), onErrorContainer = Color(0xFF5A0A1B),
    background = Color(0xFFEFF1F5), onBackground = Color(0xFF4C4F69),
    surface = Color(0xFFEFF1F5), onSurface = Color(0xFF4C4F69),
    surfaceVariant = Color(0xFFE6E9EF), onSurfaceVariant = Color(0xFF5C5F77),
    surfaceTint = Color(0xFF1E66F5),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF5F6F9),
    surfaceContainer = Color(0xFFE6E9EF), surfaceContainerHigh = Color(0xFFDCE0E8), surfaceContainerHighest = Color(0xFFCCD0DA),
    surfaceBright = Color(0xFFFFFFFF), surfaceDim = Color(0xFFCCD0DA),
    inverseSurface = Color(0xFF4C4F69), inverseOnSurface = Color(0xFFEFF1F5), inversePrimary = Color(0xFF89B4FA),
    outline = Color(0xFF8C8FA1), outlineVariant = Color(0xFFBCC0CC), scrim = Color(0xFF000000)
)

@Composable
fun PgpKeyDeskTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val status = if (dark) StatusColors(Color(0xFFA6E3A1), Color(0xFFF9E2AF), Color(0xFFF38BA8), Color(0xFF6C7086))
    else StatusColors(Color(0xFF2F7D1F), Color(0xFF9A6700), Color(0xFFD20F39), Color(0xFF6C6F85))
    androidx.compose.runtime.CompositionLocalProvider(LocalStatusColors provides status) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
    }
}

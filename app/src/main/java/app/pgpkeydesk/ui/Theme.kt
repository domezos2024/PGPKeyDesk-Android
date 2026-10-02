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

// Same Catppuccin palette as the Windows app (Mocha dark / Latte light).
private val Dark = darkColorScheme(
    primary = Color(0xFF89B4FA), onPrimary = Color(0xFF11111B),
    primaryContainer = Color(0xFF313244), onPrimaryContainer = Color(0xFFCDD6F4),
    secondary = Color(0xFF89DCEB), onSecondary = Color(0xFF11111B),
    secondaryContainer = Color(0xFF45475A), onSecondaryContainer = Color(0xFFCDD6F4),
    tertiary = Color(0xFFA6E3A1), error = Color(0xFFF38BA8), onError = Color(0xFF11111B),
    background = Color(0xFF1E1E2E), onBackground = Color(0xFFCDD6F4),
    surface = Color(0xFF1E1E2E), onSurface = Color(0xFFCDD6F4),
    surfaceVariant = Color(0xFF313244), onSurfaceVariant = Color(0xFFA6ADC8),
    surfaceContainer = Color(0xFF181825), surfaceContainerHigh = Color(0xFF313244),
    outline = Color(0xFF6C7086), outlineVariant = Color(0xFF45475A)
)

private val Light = lightColorScheme(
    primary = Color(0xFF1E66F5), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE0E8), onPrimaryContainer = Color(0xFF4C4F69),
    secondary = Color(0xFF04A5E5), tertiary = Color(0xFF40A02B), error = Color(0xFFD20F39),
    background = Color(0xFFEFF1F5), onBackground = Color(0xFF4C4F69),
    surface = Color(0xFFEFF1F5), onSurface = Color(0xFF4C4F69),
    surfaceVariant = Color(0xFFE6E9EF), onSurfaceVariant = Color(0xFF5C5F77),
    surfaceContainer = Color(0xFFE6E9EF), surfaceContainerHigh = Color(0xFFDCE0E8),
    outline = Color(0xFF8C8FA1), outlineVariant = Color(0xFFBCC0CC)
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

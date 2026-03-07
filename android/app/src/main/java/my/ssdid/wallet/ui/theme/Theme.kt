package my.ssdid.wallet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = BgPrimary,
    secondary = TextSecondary,
    background = BgPrimary,
    surface = BgSecondary,
    surfaceVariant = BgTertiary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = Border,
    outlineVariant = BorderStrong,
    error = Danger
)

@Composable
fun SsdidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = SsdidTypography,
        content = content
    )
}

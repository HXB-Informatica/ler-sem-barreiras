package pt.lersembarreiras.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = LsbColors.Primary,
    onPrimary = Color.White,
    primaryContainer = LsbColors.Sky,
    onPrimaryContainer = LsbColors.PrimaryDeep,
    secondary = LsbColors.Teal,
    onSecondary = Color.White,
    tertiary = LsbColors.Purple,
    background = LsbColors.Background,
    onBackground = LsbColors.Text,
    surface = LsbColors.Surface,
    onSurface = LsbColors.Text,
    onSurfaceVariant = LsbColors.Muted,
    error = LsbColors.Danger,
    errorContainer = Color(0xFFFFE2E0),
)

private val DarkColors = darkColorScheme(
    primary = LsbColors.Sky,
    onPrimary = LsbColors.PrimaryDeep,
    secondary = LsbColors.Teal,
    background = Color(0xFF0F1720),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF1B2430),
    onSurface = Color(0xFFF5F5F5),
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun LerSemBarreirasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

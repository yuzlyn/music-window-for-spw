package io.github.spw.island.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Windows 11 / Fluent 2 设计令牌。
 *
 * 颜色取自 Fluent 2 的 Light/Dark 中性色阶与 Windows 强调色，
 * 圆角按 WinUI 规范：控件 4dp、卡片与容器 8dp。
 */
enum class FluentAccent(
    val label: String,
    val value: String,
    val light: Color,
    val dark: Color,
) {
    Blue("蓝", "blue", Color(0xFF005FB8), Color(0xFF60CDFF)),
    Teal("青", "teal", Color(0xFF038387), Color(0xFF4CC2FF)),
    Green("绿", "green", Color(0xFF0F7B0F), Color(0xFF6CCB5F)),
    Orange("橙", "orange", Color(0xFF9D5D00), Color(0xFFFCE100)),
    Purple("紫", "purple", Color(0xFF8764B8), Color(0xFFB4A0FF)),
    Red("红", "red", Color(0xFFC42B1C), Color(0xFFFF99A4)),
    ;

    companion object {
        fun fromValue(value: String): FluentAccent = entries.firstOrNull { it.value == value } ?: Blue
    }
}

object FluentPalette {

    // Fluent 2 中性色
    private val lightBackground = Color(0xFFF3F3F3)
    private val lightSurface = Color(0xFFFFFFFF)
    private val lightSurfaceSecondary = Color(0xFFFBFBFB)
    private val lightSurfaceTertiary = Color(0xFFF5F5F5)
    private val lightStroke = Color(0xFFE5E5E5)
    private val lightDivider = Color(0xFFEBEBEB)
    private val lightTextPrimary = Color(0xFF1A1A1A)
    private val lightTextSecondary = Color(0xFF5D5D5D)

    private val darkBackground = Color(0xFF202020)
    private val darkSurface = Color(0xFF2C2C2C)
    private val darkSurfaceSecondary = Color(0xFF272727)
    private val darkSurfaceTertiary = Color(0xFF323232)
    private val darkStroke = Color(0xFF3A3A3A)
    private val darkDivider = Color(0xFF333333)
    private val darkTextPrimary = Color(0xFFFFFFFF)
    private val darkTextSecondary = Color(0xFFA0A0A0)

    fun windowBackground(dark: Boolean): Color = if (dark) darkBackground else lightBackground

    /** 面板容器色，同时用于窗口背景，保证圆角处无色差。 */
    fun cardSurface(dark: Boolean): Color = if (dark) darkSurfaceSecondary else lightSurface

    fun scheme(dark: Boolean, accent: FluentAccent): ColorScheme {
        val accentColor = if (dark) accent.dark else accent.light
        val onAccent = if (dark) Color(0xFF000000) else Color(0xFFFFFFFF)

        return if (dark) {
            darkColorScheme(
                primary = accentColor,
                onPrimary = onAccent,
                primaryContainer = Color(0xFF1F3A55),
                onPrimaryContainer = accentColor,
                secondary = darkTextSecondary,
                onSecondary = darkBackground,
                secondaryContainer = darkSurfaceTertiary,
                onSecondaryContainer = darkTextPrimary,
                tertiary = accentColor,
                onTertiary = onAccent,
                background = darkBackground,
                onBackground = darkTextPrimary,
                surface = darkSurfaceSecondary,
                onSurface = darkTextPrimary,
                surfaceVariant = darkSurfaceTertiary,
                onSurfaceVariant = darkTextSecondary,
                surfaceTint = accentColor,
                outline = darkStroke,
                outlineVariant = darkDivider,
                error = Color(0xFFFF99A4),
                onError = Color(0xFF000000),
                errorContainer = Color(0xFF442726),
                onErrorContainer = Color(0xFFFF99A4),
                scrim = Color(0x80000000),
                surfaceBright = Color(0xFF383838),
                surfaceDim = Color(0xFF1C1C1C),
                surfaceContainerLowest = Color(0xFF1A1A1A),
                surfaceContainerLow = Color(0xFF272727),
                surfaceContainer = Color(0xFF2C2C2C),
                surfaceContainerHigh = Color(0xFF323232),
                surfaceContainerHighest = Color(0xFF383838),
                inverseSurface = darkTextPrimary,
                inverseOnSurface = darkBackground,
                inversePrimary = accent.light,
            )
        } else {
            lightColorScheme(
                primary = accentColor,
                onPrimary = onAccent,
                primaryContainer = Color(0xFFDDEBF7),
                onPrimaryContainer = accentColor,
                secondary = lightTextSecondary,
                onSecondary = lightSurface,
                secondaryContainer = lightSurfaceTertiary,
                onSecondaryContainer = lightTextPrimary,
                tertiary = accentColor,
                onTertiary = onAccent,
                background = lightBackground,
                onBackground = lightTextPrimary,
                surface = lightSurface,
                onSurface = lightTextPrimary,
                surfaceVariant = lightSurfaceTertiary,
                onSurfaceVariant = lightTextSecondary,
                surfaceTint = accentColor,
                outline = lightStroke,
                outlineVariant = lightDivider,
                error = Color(0xFFC42B1C),
                onError = Color(0xFFFFFFFF),
                errorContainer = Color(0xFFFDE7E9),
                onErrorContainer = Color(0xFFC42B1C),
                scrim = Color(0x40000000),
                surfaceBright = Color(0xFFFFFFFF),
                surfaceDim = Color(0xFFE8E8E8),
                surfaceContainerLowest = Color(0xFFFFFFFF),
                surfaceContainerLow = lightSurfaceSecondary,
                surfaceContainer = lightSurface,
                surfaceContainerHigh = Color(0xFFF7F7F7),
                surfaceContainerHighest = lightSurfaceTertiary,
                inverseSurface = lightTextPrimary,
                inverseOnSurface = lightSurface,
                inversePrimary = accent.dark,
            )
        }
    }
}

private fun text(size: Int, line: Int, weight: FontWeight) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
)

/** Windows 字阶：Caption 12 / Body 14 / Subtitle 20 / Title 28。 */
val FluentTypography = Typography(
    displayLarge = text(68, 92, FontWeight.SemiBold),
    displayMedium = text(60, 72, FontWeight.SemiBold),
    displaySmall = text(48, 56, FontWeight.SemiBold),
    headlineLarge = text(34, 40, FontWeight.SemiBold),
    headlineMedium = text(28, 36, FontWeight.SemiBold),
    headlineSmall = text(24, 32, FontWeight.SemiBold),
    titleLarge = text(20, 28, FontWeight.SemiBold),
    titleMedium = text(14, 20, FontWeight.SemiBold),
    titleSmall = text(12, 16, FontWeight.SemiBold),
    bodyLarge = text(16, 22, FontWeight.Normal),
    bodyMedium = text(14, 20, FontWeight.Normal),
    bodySmall = text(12, 16, FontWeight.Normal),
    labelLarge = text(14, 20, FontWeight.SemiBold),
    labelMedium = text(12, 16, FontWeight.Normal),
    labelSmall = text(11, 14, FontWeight.Normal),
)

val FluentShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

object FluentSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
}

@Composable
fun FluentTheme(
    dark: Boolean,
    accent: FluentAccent,
    content: @Composable () -> Unit,
) {
    val scheme = remember(dark, accent) { FluentPalette.scheme(dark, accent) }
    MaterialTheme(
        colorScheme = scheme,
        typography = FluentTypography,
        shapes = FluentShapes,
        content = content,
    )
}

package com.bossxor.harugyeol.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.bossxor.harugyeol.R

// 값의 기준: docs/design-system.md 2~3절

class HaruColors(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val outline: Color,
    val text: Color,
    val textSub: Color,
    val dashed: Color,
    val stripeBg: Color,
    val stripe: Color,
    val achievedBg: Color,
    val achievedText: Color,
    val errorBg: Color,
    val errorText: Color,
)

val LightHaru = HaruColors(
    dark = false,
    bg = Color(0xFFF4F7FC), surface = Color.White, outline = Color(0xFFDBE3EF),
    text = Color(0xFF19263D), textSub = Color(0xFF607089), dashed = Color(0xFF93A4BD),
    stripeBg = Color(0xFFEDF2F9), stripe = Color(0xFFD4DEEC),
    achievedBg = Color(0xFFE4F4EF), achievedText = Color(0xFF216954),
    errorBg = Color(0xFFFCEBED), errorText = Color(0xFFAD3547),
)

val DarkHaru = HaruColors(
    dark = true,
    bg = Color(0xFF0D1422), surface = Color(0xFF172238), outline = Color(0xFF2A3852),
    text = Color(0xFFEAF0FC), textSub = Color(0xFFA5B5CD), dashed = Color(0xFF647899),
    stripeBg = Color(0xFF1E2B43), stripe = Color(0xFF344561),
    achievedBg = Color(0xFF1C3936), achievedText = Color(0xFF9AD5C1),
    errorBg = Color(0xFF422735), errorText = Color(0xFFF1A5B0),
)

class HabitPalette(
    val fill: Color, val onFill: Color, val deep: Color, val tint: Color,
    val darkText: Color, val darkTint: Color,
)

/** colorIndex 0 빨강, 1 파랑, 2 노랑, 3 초록 */
val HabitPalettes = listOf(
    HabitPalette(Color(0xFFBE4C60), Color.White, Color(0xFF9E344B), Color(0xFFFBECEF), Color(0xFFEDA1B0), Color(0xFF3F2938)),
    HabitPalette(Color(0xFF466ED5), Color.White, Color(0xFF3156B5), Color(0xFFEAF0FF), Color(0xFFA8BFFA), Color(0xFF233555)),
    HabitPalette(Color(0xFFD9A54D), Color(0xFF342510), Color(0xFF825713), Color(0xFFFFF4DF), Color(0xFFE5C38B), Color(0xFF3B332B)),
    HabitPalette(Color(0xFF287D68), Color.White, Color(0xFF216954), Color(0xFFE4F4EF), Color(0xFF9AD5C1), Color(0xFF1C3936)),
)

fun palette(index: Int) = HabitPalettes[index.coerceIn(0, HabitPalettes.lastIndex)]
fun HabitPalette.accent(c: HaruColors) = if (c.dark) darkText else deep
fun HabitPalette.tintBg(c: HaruColors) = if (c.dark) darkTint else tint

val LocalHaru = staticCompositionLocalOf { LightHaru }

private val Jua = FontFamily(Font(R.font.jua_regular))
private val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
)

private fun style(f: FontFamily, size: TextUnit, line: TextUnit, w: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = f, fontSize = size, lineHeight = line, fontWeight = w)

object HaruType {
    val display = style(Jua, 32.sp, 40.sp)
    val title = style(Pretendard, 24.sp, 32.sp, FontWeight.SemiBold)
    val headline = style(Pretendard, 18.sp, 26.sp, FontWeight.SemiBold)
    val button = style(Pretendard, 16.sp, 24.sp, FontWeight.SemiBold)
    val body = style(Pretendard, 16.sp, 24.sp)
    val bodyS = style(Pretendard, 14.sp, 20.sp)
    val number = style(Pretendard, 16.sp, 24.sp, FontWeight.SemiBold)
    val caption = style(Pretendard, 12.sp, 16.sp)
}

@Composable
fun HaruTheme(dark: Boolean = isSystemInDarkTheme(), theme: String = "SYSTEM", content: @Composable () -> Unit) {
    val resolvedDark = when (theme) { "DARK" -> true; "LIGHT" -> false; else -> dark }
    val colors = if (resolvedDark) DarkHaru else LightHaru
    val base = if (resolvedDark) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()
    val scheme = base.copy(
        primary = if (resolvedDark) HabitPalettes[1].darkText else HabitPalettes[1].fill,
        onPrimary = if (resolvedDark) colors.bg else Color.White,
        primaryContainer = HabitPalettes[1].tintBg(colors), onPrimaryContainer = HabitPalettes[1].accent(colors),
        secondary = HabitPalettes[3].accent(colors), onSecondary = if (resolvedDark) colors.bg else Color.White,
        secondaryContainer = HabitPalettes[3].tintBg(colors), onSecondaryContainer = HabitPalettes[3].accent(colors),
        tertiary = HabitPalettes[2].accent(colors), onTertiary = if (resolvedDark) colors.bg else Color.White,
        tertiaryContainer = HabitPalettes[2].tintBg(colors), onTertiaryContainer = HabitPalettes[2].accent(colors),
        surface = colors.surface, background = colors.bg, onSurface = colors.text, onBackground = colors.text,
        surfaceVariant = colors.stripeBg, onSurfaceVariant = colors.textSub,
        surfaceDim = colors.bg, surfaceBright = colors.surface,
        surfaceContainerLowest = colors.bg, surfaceContainerLow = colors.surface,
        surfaceContainer = colors.surface, surfaceContainerHigh = colors.stripeBg,
        surfaceContainerHighest = if (resolvedDark) Color(0xFF25334C) else Color(0xFFE5ECF6),
        outline = colors.dashed, outlineVariant = colors.outline, surfaceTint = colors.surface,
        error = colors.errorText, onError = if (resolvedDark) colors.bg else Color.White,
        errorContainer = colors.errorBg, onErrorContainer = colors.errorText,
        inverseSurface = if (resolvedDark) LightHaru.surface else DarkHaru.surface,
        inverseOnSurface = if (resolvedDark) LightHaru.text else DarkHaru.text,
        inversePrimary = if (resolvedDark) HabitPalettes[1].fill else HabitPalettes[1].darkText,
    )
    val typography = androidx.compose.material3.Typography(
        displayLarge = HaruType.display, titleLarge = HaruType.title, titleMedium = HaruType.headline,
        titleSmall = HaruType.button, bodyLarge = HaruType.body, bodyMedium = HaruType.bodyS,
        bodySmall = HaruType.caption, labelLarge = HaruType.button, labelMedium = HaruType.bodyS,
        labelSmall = HaruType.caption,
    )
    androidx.compose.material3.MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(LocalHaru provides colors, content = content)
    }
}

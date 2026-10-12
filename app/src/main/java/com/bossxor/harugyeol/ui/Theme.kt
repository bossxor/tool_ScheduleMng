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
    bg = Color(0xFFFFFDF7), surface = Color(0xFFFFFFFF), outline = Color(0xFF2C2C2A),
    text = Color(0xFF2C2C2A), textSub = Color(0xFF5F5E5A), dashed = Color(0xFF888780),
    stripeBg = Color(0xFFF1EFE8), stripe = Color(0xFFD3D1C7),
    achievedBg = Color(0xFFEAF3DE), achievedText = Color(0xFF27500A),
    errorBg = Color(0xFFFCEBEB), errorText = Color(0xFFA32D2D),
)

val DarkHaru = HaruColors(
    dark = true,
    bg = Color(0xFF1B1B1F), surface = Color(0xFF26262C), outline = Color(0xFFF4F1EA),
    text = Color(0xFFF4F1EA), textSub = Color(0xFFB4B2A9), dashed = Color(0xFF888780),
    stripeBg = Color(0xFF2C2C2A), stripe = Color(0xFF444441),
    achievedBg = Color(0xFF173404), achievedText = Color(0xFFC0DD97),
    errorBg = Color(0xFF501313), errorText = Color(0xFFF09595),
)

class HabitPalette(
    val fill: Color, val onFill: Color, val deep: Color, val tint: Color,
    val darkText: Color, val darkTint: Color,
)

/** colorIndex 0 빨강, 1 파랑, 2 노랑, 3 초록 */
val HabitPalettes = listOf(
    HabitPalette(Color(0xFFE24B4A), Color.White, Color(0xFFA32D2D), Color(0xFFFCEBEB), Color(0xFFF09595), Color(0xFF791F1F)),
    HabitPalette(Color(0xFF378ADD), Color.White, Color(0xFF185FA5), Color(0xFFE6F1FB), Color(0xFF85B7EB), Color(0xFF0C447C)),
    HabitPalette(Color(0xFFEF9F27), Color(0xFF412402), Color(0xFF854F0B), Color(0xFFFAEEDA), Color(0xFFFAC775), Color(0xFF633806)),
    HabitPalette(Color(0xFF639922), Color.White, Color(0xFF3B6D11), Color(0xFFEAF3DE), Color(0xFF97C459), Color(0xFF27500A)),
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
    val title = style(Jua, 24.sp, 32.sp)
    val headline = style(Jua, 18.sp, 26.sp)
    val button = style(Jua, 16.sp, 24.sp)
    val body = style(Pretendard, 16.sp, 24.sp)
    val bodyS = style(Pretendard, 14.sp, 20.sp)
    val number = style(Pretendard, 16.sp, 24.sp, FontWeight.SemiBold)
    val caption = style(Pretendard, 12.sp, 16.sp)
}

@Composable
fun HaruTheme(dark: Boolean = isSystemInDarkTheme(), theme: String = "SYSTEM", content: @Composable () -> Unit) {
    val resolvedDark = when (theme) { "DARK" -> true; "LIGHT" -> false; else -> dark }
    val colors = if (resolvedDark) DarkHaru else LightHaru
    val scheme = if (resolvedDark) androidx.compose.material3.darkColorScheme(surface = colors.surface, background = colors.bg, onSurface = colors.text, onBackground = colors.text)
        else androidx.compose.material3.lightColorScheme(surface = colors.surface, background = colors.bg, onSurface = colors.text, onBackground = colors.text)
    androidx.compose.material3.MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalHaru provides colors, content = content)
    }
}

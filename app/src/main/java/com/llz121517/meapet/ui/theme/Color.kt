package com.llz121517.meapet.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// ── Default Purple ────────────────────────────

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

private val DefaultLight = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
)
private val DefaultDark = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
)

// ── Ocean (Blue) ──────────────────────────────

private val OceanLight = lightColorScheme(
    primary = Color(0xFF006492),
    secondary = Color(0xFF4F5B62),
    tertiary = Color(0xFF6C5D7B),
    background = Color(0xFFF8FDFF),
    surface = Color(0xFFF8FDFF),
)
private val OceanDark = darkColorScheme(
    primary = Color(0xFF82CFFF),
    secondary = Color(0xFFBBC7CE),
    tertiary = Color(0xFFD3BDE4),
    background = Color(0xFF1A1C1E),
    surface = Color(0xFF1A1C1E),
)

// ── Forest (Green) ───────────────────────────

private val ForestLight = lightColorScheme(
    primary = Color(0xFF2E7D32),
    secondary = Color(0xFF54634D),
    tertiary = Color(0xFF3F6655),
    background = Color(0xFFF8FDF5),
    surface = Color(0xFFF8FDF5),
)
private val ForestDark = darkColorScheme(
    primary = Color(0xFFA9D99E),
    secondary = Color(0xFFBCCCB2),
    tertiary = Color(0xFFA6D3BB),
    background = Color(0xFF1B1F19),
    surface = Color(0xFF1B1F19),
)

// ── Sunset (Orange/Warm) ─────────────────────

private val SunsetLight = lightColorScheme(
    primary = Color(0xFFB85C00),
    secondary = Color(0xFF765A40),
    tertiary = Color(0xFF6B5D4F),
    background = Color(0xFFFFF8F4),
    surface = Color(0xFFFFF8F4),
)
private val SunsetDark = darkColorScheme(
    primary = Color(0xFFFFB870),
    secondary = Color(0xFFDAC1A8),
    tertiary = Color(0xFFD9C5B2),
    background = Color(0xFF201A16),
    surface = Color(0xFF201A16),
)

// ── Rose (Pink) ──────────────────────────────

private val RoseLight = lightColorScheme(
    primary = Color(0xFFB54E6A),
    secondary = Color(0xFF775764),
    tertiary = Color(0xFF9C4E6E),
    background = Color(0xFFFFF8F9),
    surface = Color(0xFFFFF8F9),
)
private val RoseDark = darkColorScheme(
    primary = Color(0xFFFFB1C2),
    secondary = Color(0xFFD7C1C8),
    tertiary = Color(0xFFE5B2C6),
    background = Color(0xFF201A1C),
    surface = Color(0xFF201A1C),
)

// ── Monochrome (Neutral) ─────────────────────

private val MonoLight = lightColorScheme(
    primary = Color(0xFF4A4A4A),
    secondary = Color(0xFF5E5E5E),
    tertiary = Color(0xFF6F6F6F),
    background = Color(0xFFFBFBFB),
    surface = Color(0xFFFBFBFB),
)
private val MonoDark = darkColorScheme(
    primary = Color(0xFFB8B8B8),
    secondary = Color(0xFFB0B0B0),
    tertiary = Color(0xFFA8A8A8),
    background = Color(0xFF1A1A1A),
    surface = Color(0xFF1A1A1A),
)

// ── Sky (晴空) — 清亮天蓝 ─────────────────────

private val SkyLight = lightColorScheme(
    primary = Color(0xFF5B8FA8),
    secondary = Color(0xFF6A8A9A),
    tertiary = Color(0xFF7A9AAA),
    background = Color(0xFFF5FAFC),
    surface = Color(0xFFF5FAFC),
)
private val SkyDark = darkColorScheme(
    primary = Color(0xFF8FC4DE),
    secondary = Color(0xFFAAC0CC),
    tertiary = Color(0xFFBCCEDC),
    background = Color(0xFF1A2228),
    surface = Color(0xFF1A2228),
)

// ── Sage (鼠尾草) — 柔和灰绿 ──────────────────

private val SageLight = lightColorScheme(
    primary = Color(0xFF6B9E7A),
    secondary = Color(0xFF6A8A72),
    tertiary = Color(0xFF7A9A84),
    background = Color(0xFFF5FAF6),
    surface = Color(0xFFF5FAF6),
)
private val SageDark = darkColorScheme(
    primary = Color(0xFF8FC8A0),
    secondary = Color(0xFFA0BCA8),
    tertiary = Color(0xFFB0CCB8),
    background = Color(0xFF18201A),
    surface = Color(0xFF18201A),
)

// ── Lilac (丁香) — 淡雅紫灰 ───────────────────

private val LilacLight = lightColorScheme(
    primary = Color(0xFF9A7FA8),
    secondary = Color(0xFF7A6A86),
    tertiary = Color(0xFF96809E),
    background = Color(0xFFFAF8FC),
    surface = Color(0xFFFAF8FC),
)
private val LilacDark = darkColorScheme(
    primary = Color(0xFFC0A8D0),
    secondary = Color(0xFFB0A0BC),
    tertiary = Color(0xFFC2B0CC),
    background = Color(0xFF1E1A24),
    surface = Color(0xFF1E1A24),
)

// ── Terracotta (陶土) — 暖棕红褐 ──────────────

private val TerracottaLight = lightColorScheme(
    primary = Color(0xFFA87A6A),
    secondary = Color(0xFF8A7068),
    tertiary = Color(0xFF9A7A72),
    background = Color(0xFFFCF8F6),
    surface = Color(0xFFFCF8F6),
)
private val TerracottaDark = darkColorScheme(
    primary = Color(0xFFD6A898),
    secondary = Color(0xFFBEA8A0),
    tertiary = Color(0xFFCCB0A8),
    background = Color(0xFF241C18),
    surface = Color(0xFF241C18),
)

// ── Amber (琥珀) — 温润金棕 ───────────────────

private val AmberLight = lightColorScheme(
    primary = Color(0xFFB59A5A),
    secondary = Color(0xFF8A7E5E),
    tertiary = Color(0xFF9A8E6E),
    background = Color(0xFFFCFAF2),
    surface = Color(0xFFFCFAF2),
)
private val AmberDark = darkColorScheme(
    primary = Color(0xFFDEC88A),
    secondary = Color(0xFFBAAE8E),
    tertiary = Color(0xFFCABE9E),
    background = Color(0xFF222016),
    surface = Color(0xFF222016),
)

// ── Steel (钢蓝) — 冷灰蓝调 ───────────────────

private val SteelLight = lightColorScheme(
    primary = Color(0xFF6A7A8A),
    secondary = Color(0xFF6E7A84),
    tertiary = Color(0xFF7E8A96),
    background = Color(0xFFF4F6F8),
    surface = Color(0xFFF4F6F8),
)
private val SteelDark = darkColorScheme(
    primary = Color(0xFF94A8BC),
    secondary = Color(0xFFA0ACB8),
    tertiary = Color(0xFFB0BCC8),
    background = Color(0xFF161A20),
    surface = Color(0xFF161A20),
)

// ── Preset registry ──────────────────────────

/** 颜色预设定义。 */
@Immutable
data class ThemePreset(
    val id: String,
    val name: String,
    val light: ColorScheme,
    val dark: ColorScheme,
    /** 预览主色（浅色 + 深色）。 */
    val colorLight: Color,
    val colorDark: Color,
)

/** 所有可用颜色预设。 */
val THEME_PRESETS: List<ThemePreset> = listOf(
    ThemePreset(
        id = "default", name = "紫罗兰",
        light = DefaultLight, dark = DefaultDark,
        colorLight = Purple40, colorDark = Purple80,
    ),
    ThemePreset(
        id = "ocean", name = "海洋",
        light = OceanLight, dark = OceanDark,
        colorLight = OceanLight.primary, colorDark = OceanDark.primary,
    ),
    ThemePreset(
        id = "forest", name = "森林",
        light = ForestLight, dark = ForestDark,
        colorLight = ForestLight.primary, colorDark = ForestDark.primary,
    ),
    ThemePreset(
        id = "sunset", name = "日落",
        light = SunsetLight, dark = SunsetDark,
        colorLight = SunsetLight.primary, colorDark = SunsetDark.primary,
    ),
    ThemePreset(
        id = "rose", name = "玫瑰",
        light = RoseLight, dark = RoseDark,
        colorLight = RoseLight.primary, colorDark = RoseDark.primary,
    ),
    ThemePreset(
        id = "mono", name = "单色",
        light = MonoLight, dark = MonoDark,
        colorLight = MonoLight.primary, colorDark = MonoDark.primary,
    ),
    ThemePreset(
        id = "sky", name = "晴空",
        light = SkyLight, dark = SkyDark,
        colorLight = SkyLight.primary, colorDark = SkyDark.primary,
    ),
    ThemePreset(
        id = "sage", name = "鼠尾草",
        light = SageLight, dark = SageDark,
        colorLight = SageLight.primary, colorDark = SageDark.primary,
    ),
    ThemePreset(
        id = "lilac", name = "丁香",
        light = LilacLight, dark = LilacDark,
        colorLight = LilacLight.primary, colorDark = LilacDark.primary,
    ),
    ThemePreset(
        id = "terracotta", name = "陶土",
        light = TerracottaLight, dark = TerracottaDark,
        colorLight = TerracottaLight.primary, colorDark = TerracottaDark.primary,
    ),
    ThemePreset(
        id = "amber", name = "琥珀",
        light = AmberLight, dark = AmberDark,
        colorLight = AmberLight.primary, colorDark = AmberDark.primary,
    ),
    ThemePreset(
        id = "steel", name = "钢蓝",
        light = SteelLight, dark = SteelDark,
        colorLight = SteelLight.primary, colorDark = SteelDark.primary,
    ),
)

/** 按 id 查找预设，找不到返回 default。 */
fun findPreset(id: String): ThemePreset =
    THEME_PRESETS.find { it.id == id } ?: THEME_PRESETS.first()

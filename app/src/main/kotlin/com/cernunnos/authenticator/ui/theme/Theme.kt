package com.cernunnos.authenticator.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

// ── Cernunnos ERP design tokens (from src/cernunnos/static/css/main.css) ──

// Primary violet
val CernunnosPrimary = Color(0xFF7D07F8)
val CernunnosPrimaryLight = Color(0xFF9574EB)
val CernunnosPrimaryDark = Color(0xFF5A05B8)

// Secondary green accent
val CernunnosSecondary = Color(0xFF00FE94)
val CernunnosAccentGreen = Color(0xFF0CA700)

// Dark theme backgrounds
val CernunnosBg = Color(0xFF0D0D1A)
val CernunnosBgCard = Color(0xFF14142A)
val CernunnosBgCard2 = Color(0xFF1A1A35)
val CernunnosBgHover = Color(0x147D07F8) // rgba(125,7,248,0.08)

// Borders & text
val CernunnosBorder = Color(0xFF2A2A50)
val CernunnosText = Color(0xFFE8E8FF)
val CernunnosTextMuted = Color(0xFF8888AA)

// Status colors
val CernunnosDanger = Color(0xFFFF4757)
val CernunnosWarning = Color(0xFFFFA502)
val CernunnosInfo = Color(0xFF1E90FF)
val CernunnosSuccess = Color(0xFF0CA700)

// Light theme variants
val CernunnosBgLight = Color(0xFFF5F5FA)
val CernunnosBgCardLight = Color(0xFFFFFFFF)
val CernunnosBgCard2Light = Color(0xFFEDEDF5)
val CernunnosBorderLight = Color(0xFFD8D8E5)
val CernunnosTextLight = Color(0xFF1A1A2E)
val CernunnosTextMutedLight = Color(0xFF6B6B8A)
val CernunnosDangerLight = Color(0xFFE03141)
val CernunnosWarningLight = Color(0xFFE8950A)
val CernunnosInfoLight = Color(0xFF1778C9)
val CernunnosSecondaryLight = Color(0xFF00D982)

private val DarkColors = darkColorScheme(
    primary = CernunnosPrimary,
    onPrimary = Color.White,
    primaryContainer = CernunnosPrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = CernunnosSecondary,
    onSecondary = Color(0xFF0D0D1A),
    secondaryContainer = CernunnosAccentGreen,
    onSecondaryContainer = Color.White,
    tertiary = CernunnosPrimaryLight,
    onTertiary = Color.White,
    background = CernunnosBg,
    onBackground = CernunnosText,
    surface = CernunnosBgCard,
    onSurface = CernunnosText,
    surfaceVariant = CernunnosBgCard2,
    onSurfaceVariant = CernunnosTextMuted,
    outline = CernunnosBorder,
    outlineVariant = CernunnosBorder,
    error = CernunnosDanger,
    onError = Color.White,
    errorContainer = CernunnosDanger,
    onErrorContainer = Color.White,
)

private val LightColors = lightColorScheme(
    primary = CernunnosPrimary,
    onPrimary = Color.White,
    primaryContainer = CernunnosPrimary,
    onPrimaryContainer = Color.White,
    secondary = CernunnosSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = CernunnosAccentGreen,
    onSecondaryContainer = Color.White,
    tertiary = CernunnosPrimaryLight,
    onTertiary = Color.White,
    background = CernunnosBgLight,
    onBackground = CernunnosTextLight,
    surface = CernunnosBgCardLight,
    onSurface = CernunnosTextLight,
    surfaceVariant = CernunnosBgCard2Light,
    onSurfaceVariant = CernunnosTextMutedLight,
    outline = CernunnosBorderLight,
    outlineVariant = CernunnosBorderLight,
    error = CernunnosDangerLight,
    onError = Color.White,
    errorContainer = CernunnosDangerLight,
    onErrorContainer = Color.White,
)

// Cernunnos shapes: --radius: 12px, --radius-sm: 8px
private val CernunnosShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(20.dp), // auth card uses 20px
)

@Composable
fun CernunnosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = CernunnosShapes,
        content = content,
    )
}

/** Theme mode: "dark", "light", or "system" */
enum class ThemeMode(val key: String) {
    DARK("dark"),
    LIGHT("light"),
    SYSTEM("system");

    companion object {
        fun fromKey(key: String): ThemeMode = entries.find { it.key == key } ?: DARK
    }
}

/**
 * Centralized FilterChip colors for consistent styling across all screens.
 * Uses theme colors so it adapts to dark/light mode automatically.
 */
@Composable
fun cernunnosChipColors() = androidx.compose.material3.FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
)

// ── Spacing constants for consistent UI ──
val SpacingXS = 4.dp
val SpacingS = 8.dp
val SpacingM = 12.dp
val SpacingL = 16.dp
val SpacingXL = 24.dp
val SpacingXXL = 32.dp

package dev.jasper.ishav2.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * isha's two palettes: warm cream in daylight, espresso at night.
 *
 * Token names describe their role, not their literal color: "cream" is the
 * page background in both palettes. Components read them through the
 * composable getters at the bottom of this file, so switching the theme at
 * runtime needs no changes at the call sites.
 */
@Immutable
data class IshaPalette(
    val cream: Color,
    val creamDeep: Color,
    val surfaceHi: Color,
    val surfaceLo: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val accentDeep: Color,
    val accentSoft: Color,
    val success: Color,
    val warn: Color,
    val danger: Color,
    val codeBg: Color,
    val stroke: Color,
    val shadowLight: Color,
    val shadowDark: Color,
    val scrim: Color,
    val onAccent: Color
)

val LightPalette = IshaPalette(
    cream = Color(0xFFFAF3E1),
    creamDeep = Color(0xFFF3E4C4),
    surfaceHi = Color(0xFFFFFDF6),
    surfaceLo = Color(0xFFF8EBCF),
    ink = Color(0xFF241F19),
    muted = Color(0xFF7C6F5D),
    accent = Color(0xFFFF6D1F),
    accentDeep = Color(0xFFC2410C),
    accentSoft = Color(0xFFFFE0CC),
    success = Color(0xFF2E7D5B),
    warn = Color(0xFFB7791F),
    danger = Color(0xFFB3261E),
    codeBg = Color(0xFFF0E1C2),
    stroke = Color(0x1A6B5A3E),
    shadowLight = Color(0x99FFFFFF),
    shadowDark = Color(0x2E3A2A12),
    scrim = Color(0x52241F19),
    onAccent = Color(0xFFFFF6E8)
)

val DarkPalette = IshaPalette(
    cream = Color(0xFF17130F),
    creamDeep = Color(0xFF0F0C08),
    surfaceHi = Color(0xFF2C251D),
    surfaceLo = Color(0xFF211B15),
    ink = Color(0xFFF7EEDD),
    muted = Color(0xFFA9987F),
    accent = Color(0xFFFF8A3D),
    accentDeep = Color(0xFFDD5711),
    accentSoft = Color(0xFF3A2214),
    success = Color(0xFF63C79B),
    warn = Color(0xFFE8B25A),
    danger = Color(0xFFFF6F61),
    codeBg = Color(0xFF221B14),
    stroke = Color(0x1FFFF0D0),
    shadowLight = Color(0x1AFFF0D0),
    shadowDark = Color(0x99000000),
    scrim = Color(0xB3000000),
    onAccent = Color(0xFF1B1108)
)

val LocalIshaPalette = staticCompositionLocalOf { DarkPalette }

internal fun IshaPalette.colorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSoft,
        onPrimaryContainer = ink,
        secondary = accentDeep,
        onSecondary = onAccent,
        secondaryContainer = creamDeep,
        onSecondaryContainer = ink,
        tertiary = success,
        onTertiary = if (dark) Color(0xFF07231A) else Color.White,
        background = cream,
        onBackground = ink,
        surface = cream,
        onSurface = ink,
        surfaceVariant = creamDeep,
        onSurfaceVariant = muted,
        surfaceContainerLowest = creamDeep,
        surfaceContainerLow = cream,
        surfaceContainer = surfaceLo,
        surfaceContainerHigh = surfaceHi,
        surfaceContainerHighest = surfaceHi,
        surfaceDim = creamDeep,
        surfaceBright = surfaceHi,
        inverseSurface = ink,
        inverseOnSurface = cream,
        inversePrimary = accentDeep,
        outline = stroke,
        outlineVariant = stroke,
        scrim = scrim,
        error = danger,
        onError = if (dark) Color(0xFF2A0906) else Color.White
    )
}

// Read-only accessors keep component code palette-agnostic.

val IshaCream: Color
    @Composable @ReadOnlyComposable get() = LocalIshaPalette.current.cream

val IshaCreamDeep: Color
    @Composable @ReadOnlyComposable get() = LocalIshaPalette.current.creamDeep

val IshaSurfaceHi: Color
    @Composable @ReadOnlyComposable get() = LocalIshaPalette.current.surfaceHi

val IshaSurfaceLo: Color
    @Composable @ReadOnlyComposable get() = LocalIshaPalette.current.surfaceLo

val IshaInk: Color
    @Composable @ReadOnlyComposable get() = LocalIshaPalette.current.ink

val IshaMuted: Color
    @Composable @ReadOnlyComposable get() = LocalIshaPalette.current.muted

val IshaAccent: Color
    @Composable @ReadOnlyComposable get() = LocalIshaPalette.current.accent

val IshaAccentDeep: Color
    @Composable @ReadOnlyComposable get() = LocalIshaPalette.current.accentDeep

val IshaAccentSoft: Color
    @Composable @ReadOnlyComposable get() = LocalIshaPalette.current.accentSoft

val IshaSuccess: Color
    @Composable @ReadOnlyComposable get() = LocalIshaPalette.current.success

val IshaWarn: Color
    @Composable @ReadOnlyComposable get() = LocalIshaPalette.current.warn

val IshaDanger: Color
    @Composable @ReadOnlyComposable get() = LocalIshaPalette.current.danger

val IshaCodeBg: Color
    @Composable @ReadOnlyComposable get() = LocalIshaPalette.current.codeBg

val IshaStroke: Color
    @Composable @ReadOnlyComposable get() = LocalIshaPalette.current.stroke

val IshaShadowLight: Color
    @Composable @ReadOnlyComposable get() = LocalIshaPalette.current.shadowLight

val IshaShadowDark: Color
    @Composable @ReadOnlyComposable get() = LocalIshaPalette.current.shadowDark

val IshaScrim: Color
    @Composable @ReadOnlyComposable get() = LocalIshaPalette.current.scrim

val IshaOnAccent: Color
    @Composable @ReadOnlyComposable get() = LocalIshaPalette.current.onAccent

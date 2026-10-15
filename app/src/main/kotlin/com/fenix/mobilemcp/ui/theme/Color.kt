@file:Suppress("MagicNumber")

package com.fenix.mobilemcp.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ── Paleta Fénix (identidad propia del fork) ───────────────────────────────────
// Fuego: brasa (tema claro), llama (tema oscuro) y oro (acento).
// Noche: base profunda que ancla el fuego. Ningún valor heredado de otro autor.

// Primary — fuego: brasa (claro) / llama (oscuro)
val PrimaryLight = Color(0xFFC63F17)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFFFDBCB)
val OnPrimaryContainerLight = Color(0xFF3B0E00)

// Secondary — oro Fénix
val SecondaryLight = Color(0xFF7A5A12)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFFFDF9B)
val OnSecondaryContainerLight = Color(0xFF261A00)

// Tertiary — noche Fénix (azul profundo, contraste frío contra el fuego)
val TertiaryLight = Color(0xFF3E4A6B)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFDCE2F6)
val OnTertiaryContainerLight = Color(0xFF141B2E)

// Error
val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

// Surface and background — ceniza clara
val SurfaceLight = Color(0xFFFAF8F3)
val OnSurfaceLight = Color(0xFF1C1A17)
val SurfaceVariantLight = Color(0xFFEBE4DA)
val OnSurfaceVariantLight = Color(0xFF4A443C)
val OutlineLight = Color(0xFF7C756B)
val OutlineVariantLight = Color(0xFFCDC5B9)

// Dark theme
val PrimaryDark = Color(0xFFFF6A1F)
val OnPrimaryDark = Color(0xFF141B2E)
val PrimaryContainerDark = Color(0xFF8A3200)
val OnPrimaryContainerDark = Color(0xFFFFDBCB)

val SecondaryDark = Color(0xFFFFC53D)
val OnSecondaryDark = Color(0xFF3A2C00)
val SecondaryContainerDark = Color(0xFF5B4300)
val OnSecondaryContainerDark = Color(0xFFFFDF9B)

val TertiaryDark = Color(0xFFBAC4E8)
val OnTertiaryDark = Color(0xFF141B2E)
val TertiaryContainerDark = Color(0xFF353E59)
val OnTertiaryContainerDark = Color(0xFFDCE2F6)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

val SurfaceDark = Color(0xFF131519)
val OnSurfaceDark = Color(0xFFE5E1DB)
val SurfaceVariantDark = Color(0xFF4A443C)
val OnSurfaceVariantDark = Color(0xFFCDC5B9)
val OutlineDark = Color(0xFF948C82)
val OutlineVariantDark = Color(0xFF4A443C)

/** Amber used for advisory warnings (yellow triangle). ARGB 0xFFF9A825. */
val WarningAmber = Color(0xFFF9A825)

val LightColorScheme =
    lightColorScheme(
        primary = PrimaryLight,
        onPrimary = OnPrimaryLight,
        primaryContainer = PrimaryContainerLight,
        onPrimaryContainer = OnPrimaryContainerLight,
        secondary = SecondaryLight,
        onSecondary = OnSecondaryLight,
        secondaryContainer = SecondaryContainerLight,
        onSecondaryContainer = OnSecondaryContainerLight,
        tertiary = TertiaryLight,
        onTertiary = OnTertiaryLight,
        tertiaryContainer = TertiaryContainerLight,
        onTertiaryContainer = OnTertiaryContainerLight,
        error = ErrorLight,
        onError = OnErrorLight,
        errorContainer = ErrorContainerLight,
        onErrorContainer = OnErrorContainerLight,
        surface = SurfaceLight,
        onSurface = OnSurfaceLight,
        surfaceVariant = SurfaceVariantLight,
        onSurfaceVariant = OnSurfaceVariantLight,
        outline = OutlineLight,
        outlineVariant = OutlineVariantLight,
    )

val DarkColorScheme =
    darkColorScheme(
        primary = PrimaryDark,
        onPrimary = OnPrimaryDark,
        primaryContainer = PrimaryContainerDark,
        onPrimaryContainer = OnPrimaryContainerDark,
        secondary = SecondaryDark,
        onSecondary = OnSecondaryDark,
        secondaryContainer = SecondaryContainerDark,
        onSecondaryContainer = OnSecondaryContainerDark,
        tertiary = TertiaryDark,
        onTertiary = OnTertiaryDark,
        tertiaryContainer = TertiaryContainerDark,
        onTertiaryContainer = OnTertiaryContainerDark,
        error = ErrorDark,
        onError = OnErrorDark,
        errorContainer = ErrorContainerDark,
        onErrorContainer = OnErrorContainerDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        outline = OutlineDark,
        outlineVariant = OutlineVariantDark,
    )

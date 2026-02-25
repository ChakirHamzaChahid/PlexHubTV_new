package com.chakir.plexhubtv.core.designsystem

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme =
    darkColorScheme(
        primary = PlexOrange,
        secondary = PurpleGrey80,
        tertiary = Pink80,
        background = PlexBackground,
        surface = PlexBackground, // Or PlexSurface if we want cards to pop, but user asked for black. Let's stick to Background being Black.
        onPrimary = Color.Black,
        onBackground = PlexText,
        onSurface = PlexText,
        surfaceVariant = PlexSurface,
        onSurfaceVariant = PlexTextSecondary,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = Purple40,
        secondary = PurpleGrey40,
        tertiary = Pink40,
    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
     */
    )

private val MonoDarkColorScheme =
    darkColorScheme(
        primary = MonoDarkText,
        onPrimary = MonoDarkBg,
        secondary = MonoDarkText,
        onSecondary = MonoDarkBg,
        tertiary = MonoDarkText,
        onTertiary = MonoDarkBg,
        background = MonoDarkBg,
        surface = MonoDarkSurface,
        onBackground = MonoDarkText,
        onSurface = MonoDarkText,
        error = MonoDarkError,
        outline = MonoDarkOutline,
        surfaceVariant = MonoDarkSurface,
        onSurfaceVariant = MonoDarkTextMuted,
    )

private val MonoLightColorScheme =
    lightColorScheme(
        primary = MonoLightText,
        onPrimary = MonoLightBg,
        secondary = MonoLightText,
        onSecondary = MonoLightBg,
        tertiary = MonoLightText,
        onTertiary = MonoLightBg,
        background = MonoLightBg,
        surface = MonoLightSurface,
        onBackground = MonoLightText,
        onSurface = MonoLightText,
        // Add error color for light if needed, or default
        outline = MonoLightOutline,
        surfaceVariant = MonoLightSurface,
        onSurfaceVariant = MonoLightTextMuted,
    )

private val MoroccoColorScheme =
    darkColorScheme(
        primary = MorocRed,
        onPrimary = Color.White,
        secondary = MorocGreen,
        onSecondary = Color.White,
        tertiary = MorocGold,
        onTertiary = Color.Black,
        background = MorocBackground,
        surface = MorocSurface,
        onBackground = Color.White,
        onSurface = Color.White,
        error = MorocRed,
        outline = MorocGold,
        surfaceVariant = MorocSurface,
        onSurfaceVariant = Color.LightGray,
    )

private val NetflixColorScheme =
    darkColorScheme(
        primary = NetflixRed,
        onPrimary = NetflixWhite,
        secondary = NetflixRed,
        onSecondary = NetflixWhite,
        tertiary = NetflixRed,
        onTertiary = NetflixWhite,
        background = NetflixBlack,
        surface = NetflixDarkGray,
        onBackground = NetflixWhite,
        onSurface = NetflixWhite,
        error = NetflixRed,
        outline = NetflixWhite,
        surfaceVariant = NetflixDarkGray,
        onSurfaceVariant = NetflixLightGray,
    )

/**
 * Thème principal de l'application basé sur Material3.
 *
 * Supporte :
 * - Mode sombre (Force l'application en mode sombre par défaut pour l'expérience TV).
 * - Couleurs dynamiques (Android 12+).
 * - Thèmes personnalisés ("Plex" par défaut, "Mono" options).
 */
@Composable
fun PlexHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    appTheme: String = "Plex", // "Plex", "MonoDark", "MonoLight"
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when (appTheme) {
            "MonoDark" -> MonoDarkColorScheme
            "MonoLight" -> MonoLightColorScheme
            "Morocco" -> MoroccoColorScheme
            "Netflix" -> NetflixColorScheme
            "Plex" -> if (darkTheme) DarkColorScheme else LightColorScheme
            else -> if (darkTheme) DarkColorScheme else LightColorScheme
        }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity()
            if (activity == null) {
                Log.w("PlexHubTheme", "Cannot find Activity from view context, skipping window customization")
                return@SideEffect
            }
            val window = activity.window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

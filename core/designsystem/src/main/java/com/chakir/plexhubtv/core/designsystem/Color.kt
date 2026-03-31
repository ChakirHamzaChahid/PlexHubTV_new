package com.chakir.plexhubtv.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Définition des constantes de couleur de l'application.
 * Contient la palette Material3 standard ainsi que les couleurs personnalisées Plex.
 */

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Plex Colors
// Plex Colors
val PlexOrange = Color(0xFFE5A00D)
val PlexBackground = Color(0xFF000000) // Pure Black
val PlexSurface = Color(0xFF121212) // Slightly lighter for cards/surfaces
val PlexText = Color(0xFFE6E6E6) // Light Gray/White for text
val PlexTextSecondary = Color(0xFFAAAAAA) // Darker Gray for secondary text

// Mono Theme Colors (plezy/lib/theme/mono_tokens.dart)
val MonoDarkBg = Color(0xFF0E0F12)
val MonoDarkSurface = Color(0xFF15171C)
val MonoDarkOutline = Color(0x1FFFFFFF)
val MonoDarkText = Color(0xFFEDEDED)
val MonoDarkTextMuted = Color(0x99EDEDED)
val MonoDarkError = Color(0xFFB00020)

val MonoLightBg = Color(0xFFF7F7F8)
val MonoLightSurface = Color(0xFFFFFFFF)
val MonoLightOutline = Color(0x19000000)
val MonoLightText = Color(0xFF111111)
val MonoLightTextMuted = Color(0x99111111)

// Morocco Theme Colors
val MorocRed = Color(0xFFC1272D) // Flag Red
val MorocGreen = Color(0xFF006233) // Flag Green
val MorocGold = Color(0xFFFDB913) // Royal Gold Accent
val MorocBackground = Color(0xFF1A0505) // Deep Dark Red/Brown Background (Night in Marrakech)
val MorocSurface = Color(0xFF2D0A0A) // Slightly lighter for cards

// OLED Black Theme Colors
val OledBlack = Color(0xFF000000) // True black for AMOLED
val OledSurface = Color(0xFF0A0A0A) // Near-black for cards — minimal contrast to save pixels
val OledText = Color(0xFFE0E0E0) // Slightly dimmed white to reduce eye strain
val OledTextMuted = Color(0xFF808080) // Muted gray for secondary text
val OledAccent = Color(0xFF6B6B6B) // Subtle gray accent

// Netflix Colors
val NetflixRed = Color(0xFFE50914)
val NetflixBlack = Color(0xFF000000)
val NetflixDarkGray = Color(0xFF141414)
val NetflixLightGray = Color(0xFFB3B3B3)
val NetflixWhite = Color(0xFFFFFFFF)

// === CINEMA GOLD THEME ===
// Fonds — couleurs opaques pré-calculées (alpha-blend E8E2D6 sur #06060A)
val CinemaBackground = Color(0xFF06060A) // Noir profond — fond principal
val CinemaSurface = Color(0xFF0D0D10)    // Surface cards — 3% crème sur noir (opaque)
val CinemaSurfaceHigh = Color(0xFF131316) // Surface élevée — 6% crème sur noir (opaque)

// Accents
val CinemaGold = Color(0xFFC9952E)       // Accent principal — doré cinéma
val CinemaGoldDim = Color(0xFFA37A22)    // Doré foncé — states secondaires
val CinemaAccentLight = Color(0xFFD4A94A) // Doré clair — highlights

// Texte
val CinemaWhite = Color(0xFFE8E2D6)      // Texte principal — crème chaud
val CinemaGray = Color(0x8CE8E2D6)       // Texte atténué — 55% opacity
val CinemaGrayDark = Color(0x14E8E2D6)   // Bordures — 8% opacity
val CinemaTextDim = Color(0x4DE8E2D6)    // Texte discret — 30% opacity

// Sémantique
val CinemaError = Color(0xFFE53935)      // Erreurs / Live
val CinemaSuccess = Color(0xFF43A047)    // Succès / Connecté
val CinemaRed = CinemaError              // Alias pour badges EN DIRECT
val CinemaGreen = CinemaSuccess          // Alias pour statut connecté

// Effets
val CinemaOverlay = Color(0xCC06060A)    // Overlay scrim — 80% opacity

// Badges techniques (SourceSelectionDialog, TechnicalBadges)
val CinemaBadgeHdr = Color(0xFFD4A94A)       // HDR badge — doré clair (CinemaAccentLight)
val CinemaBadgeDolbyVision = Color(0xFFE91E63) // Dolby Vision — rose
val CinemaBadgeAtmos = Color(0xFF00B0FF)     // Dolby Atmos — bleu
val CinemaBadgeVideo = Color(0xFF64B5F6)     // Video codec — bleu clair
val CinemaBadgeAudio = Color(0xFFCE93D8)     // Audio codec — violet clair
val CinemaBadgeContainer = Color(0xFF90A4AE) // Container format — gris-bleu
val CinemaBadgeFileSize = Color(0xFF81C784)  // File size — vert clair
val CinemaBadgeResume = Color(0xFFFFA726)    // Resume indicator — orange

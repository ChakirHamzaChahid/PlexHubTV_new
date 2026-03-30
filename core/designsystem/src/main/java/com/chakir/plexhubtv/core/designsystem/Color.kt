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
// Fonds
val CinemaBackground = Color(0xFF070B0F) // Noir bleuté profond — fond principal
val CinemaSurface = Color(0xFF0F1520) // Surface cards — bleu nuit
val CinemaSurfaceHigh = Color(0xFF1A2336) // Surface élevée — hover/focus

// Accents
val CinemaGold = Color(0xFFFFB938) // Accent principal — or ambré premium
val CinemaGoldDim = Color(0xFF8A6520) // Or atténué — states secondaires

// Texte
val CinemaWhite = Color(0xFFF2F2F2) // Texte principal
val CinemaGray = Color(0xFF8B95A5) // Texte secondaire — bleuté
val CinemaGrayDark = Color(0xFF3D4A5C) // Séparateurs, bordures, tertiaire

// Sémantique
val CinemaError = Color(0xFFFF5252) // Erreurs / Destructif
val CinemaSuccess = Color(0xFF4CAF50) // Succès

// Effets
val CinemaGlow = Color(0x33FFB938) // Lueur dorée focus (20% opacité)
val CinemaOverlay = Color(0xCC070B0F) // Overlay scrim (80% opacité)

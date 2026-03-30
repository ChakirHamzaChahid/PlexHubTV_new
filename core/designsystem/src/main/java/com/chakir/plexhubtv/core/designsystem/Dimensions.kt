package com.chakir.plexhubtv.core.designsystem

import androidx.compose.ui.unit.dp

/**
 * Standard dimension constants for TV-safe layout spacing.
 */
object Dimensions {
    /** Standard horizontal overscan padding for TV displays (48dp). */
    val overscanHorizontal = 48.dp

    /** Standard vertical overscan padding for TV displays (48dp). */
    val overscanVertical = 48.dp
}

// === CINEMA GOLD REFONTE ===
object Dims {
    // Cards
    val CardWidthPortrait = 140.dp
    val CardHeightPortrait = 210.dp
    val CardWidthLandscape = 240.dp
    val CardHeightLandscape = 135.dp
    val CardRadius = 12.dp
    val CardElevation = 4.dp

    // Focus (Android TV D-pad navigation)
    val FocusBorderWidth = 3.dp
    val FocusScale = 1.08f
    val FocusBorderRadius = 14.dp

    // Spacing
    val ScreenPaddingH = 56.dp
    val ScreenPaddingV = 36.dp
    val RowSpacing = 32.dp
    val CardSpacing = 16.dp
    val SectionTitlePad = 16.dp

    // Hero Billboard
    val HeroBillboardHeight = 460.dp
    val HeroGradientHeight = 240.dp

    // TopBar
    val TopBarHeight = 64.dp
    val TopBarIconSize = 28.dp

    // Keyboard
    val KeySize = 48.dp
    val KeySpacing = 6.dp
    val KeyRadius = 8.dp
}

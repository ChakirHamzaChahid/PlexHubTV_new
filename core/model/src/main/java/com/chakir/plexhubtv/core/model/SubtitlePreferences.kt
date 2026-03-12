package com.chakir.plexhubtv.core.model

data class SubtitlePreferences(
    val fontSize: Int = 22, // sp
    val fontColor: Long = 0xFFFFFFFF, // White
    val backgroundColor: Long = 0x80000000, // Semi-transparent black
    val edgeType: Int = 0, // 0=None, 1=Outline, 2=DropShadow, 3=Raised, 4=Depressed
    val edgeColor: Long = 0xFF000000, // Black
)

package com.chakir.plexhubtv.core.ui

import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware

/**
 * Extracted color palette from artwork, used for dynamic gradient overlays.
 */
@Immutable
data class BackdropColors(
    val primary: Color = Color.Black,
    val secondary: Color = Color.Black,
    val tertiary: Color = Color.Black,
    val isDefault: Boolean = true,
)

private val cache = object : LinkedHashMap<String, BackdropColors>(50, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BackdropColors>?): Boolean = size > 50
}

/**
 * Extracts dominant colors from an artwork URL using Android Palette API.
 * Returns [BackdropColors] with primary/secondary/tertiary tones derived from the image.
 * Results are cached in-memory (LRU, 50 entries).
 */
@Composable
fun rememberBackdropColors(imageUrl: String?): BackdropColors {
    var colors by remember { mutableStateOf(imageUrl?.let { cache[it] } ?: BackdropColors()) }
    val context = LocalContext.current

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank()) {
            colors = BackdropColors()
            return@LaunchedEffect
        }

        cache[imageUrl]?.let {
            colors = it
            return@LaunchedEffect
        }

        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(256, 144) // Small size is sufficient for palette extraction
            .allowHardware(false) // Palette needs pixel access, not supported on hardware bitmaps
            .build()

        val result = context.imageLoader.execute(request)
        if (result is SuccessResult) {
            val drawable = result.image.asDrawable(context.resources)
            val bitmap = (drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                val palette = Palette.from(bitmap).generate()
                val fallback = 0xFF000000.toInt()
                val extracted = BackdropColors(
                    primary = Color(palette.getDarkVibrantColor(fallback)).copy(alpha = 0.4f),
                    secondary = Color(palette.getDarkMutedColor(fallback)),
                    tertiary = Color(palette.getVibrantColor(fallback)).copy(alpha = 0.35f),
                    isDefault = false,
                )
                cache[imageUrl] = extracted
                colors = extracted
            }
        }
    }

    return colors
}

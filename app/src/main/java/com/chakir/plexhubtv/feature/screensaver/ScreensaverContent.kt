package com.chakir.plexhubtv.feature.screensaver

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScreensaverContent(viewModel: ScreensaverViewModel) {
    val artworkUrls by viewModel.artworkUrls.collectAsState()
    val showClock by viewModel.showClock.collectAsState()
    val intervalSeconds by viewModel.intervalSeconds.collectAsState()

    var currentIndex by remember { mutableIntStateOf(0) }
    var currentTime by remember { mutableStateOf("") }

    // Rotate artwork
    LaunchedEffect(artworkUrls, intervalSeconds) {
        if (artworkUrls.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(intervalSeconds * 1000L)
            currentIndex = (currentIndex + 1) % artworkUrls.size
        }
    }

    // Update clock
    LaunchedEffect(showClock) {
        if (!showClock) return@LaunchedEffect
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            currentTime = timeFormat.format(Date())
            delay(30_000L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (artworkUrls.isNotEmpty()) {
            val url = artworkUrls.getOrNull(currentIndex % artworkUrls.size)
            AnimatedContent(
                targetState = url,
                transitionSpec = {
                    fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(2000),
                    ) togetherWith fadeOut(
                        animationSpec = androidx.compose.animation.core.tween(2000),
                    )
                },
                label = "screensaver_crossfade",
            ) { imageUrl ->
                if (imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Clock overlay
        if (showClock && currentTime.isNotEmpty()) {
            Text(
                text = currentTime,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                fontSize = 48.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(32.dp),
            )
        }
    }
}

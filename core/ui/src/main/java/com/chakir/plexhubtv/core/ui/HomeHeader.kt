package com.chakir.plexhubtv.core.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chakir.plexhubtv.core.model.MediaItem

/**
 * Purely informational home header — displays metadata for the currently focused media item.
 * No focusable elements here; interaction happens via the content row cards below.
 * The app-level backdrop behind handles the artwork visual.
 */
@Composable
fun HomeHeader(
    item: MediaItem?,
    modifier: Modifier = Modifier,
) {
    if (item == null) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_header")
            .semantics { contentDescription = "En vedette: ${item.title}" }
    ) {
        Crossfade(
            targetState = item,
            animationSpec = tween(400),
            label = "header_crossfade"
        ) { media ->
            // === CINEMA GOLD REFONTE ===
            val cs = MaterialTheme.colorScheme
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 48.dp, bottom = 16.dp, end = 48.dp)
                        .fillMaxWidth(0.5f)
                ) {
                    Text(
                        text = media.title,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = cs.onBackground
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Metadata Line
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        media.year?.let {
                            Text(text = it.toString(), color = cs.onBackground.copy(alpha = 0.8f))
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        media.contentRating?.let {
                            Box(
                                modifier = Modifier
                                    .background(
                                        cs.onSurfaceVariant.copy(alpha = 0.4f),
                                        RoundedCornerShape(2.dp)
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = it,
                                    color = cs.onBackground,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        val rating = media.rating
                        if (rating != null && rating > 0) {
                            Text(
                                text = String.format("%.1f", rating),
                                color = cs.tertiary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = media.summary ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onBackground.copy(alpha = 0.9f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

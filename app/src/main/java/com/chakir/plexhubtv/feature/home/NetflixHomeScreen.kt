package com.chakir.plexhubtv.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.ui.NetflixHeroBillboard

@Composable
fun NetflixHomeContent(
    heroItems: List<MediaItem>,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateUp: (() -> Unit)? = null,
) {
    val billboardButtonsFocusRequester = remember { FocusRequester() }

    var hasRequestedInitialFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hasRequestedInitialFocus) {
            billboardButtonsFocusRequester.requestFocus()
            hasRequestedInitialFocus = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_home")
            .semantics { contentDescription = "Écran d'accueil" }
    ) {
        NetflixHeroBillboard(
            items = heroItems,
            onPlay = { onAction(HomeAction.PlayMedia(it)) },
            onInfo = { onAction(HomeAction.OpenMedia(it)) },
            onNavigateDown = { /* Stay on Accueil - no navigation */ },
            onNavigateUp = onNavigateUp,
            buttonsFocusRequester = billboardButtonsFocusRequester
        )
    }
}

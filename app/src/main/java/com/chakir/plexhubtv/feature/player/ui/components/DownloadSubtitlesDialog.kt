package com.chakir.plexhubtv.feature.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.core.designsystem.NetflixLightGray
import com.chakir.plexhubtv.feature.player.controller.SubtitleSearchResult
import com.chakir.plexhubtv.feature.player.controller.SubtitleSearchService
import kotlinx.coroutines.launch

private val DialogBackground = Color(0xFF1A1A1A)

@Composable
fun DownloadSubtitlesDialog(
    subtitleSearchService: SubtitleSearchService,
    mediaTitle: String,
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
    onSubtitleDownloaded: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf(mediaTitle) }
    var results by remember { mutableStateOf<List<SubtitleSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasSearched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val searchFocusRequester = remember { FocusRequester() }
    val dialogTitle = stringResource(R.string.player_subtitle_download_title)

    // Auto-search on open
    LaunchedEffect(Unit) {
        if (query.isNotBlank()) {
            isSearching = true
            val result = subtitleSearchService.search(
                query = query,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
            )
            result.onSuccess {
                results = it
                errorMessage = null
            }.onFailure {
                errorMessage = it.message
            }
            isSearching = false
            hasSearched = true
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DialogBackground,
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .heightIn(max = 600.dp)
                    .testTag("dialog_subtitle_download")
                    .semantics { contentDescription = dialogTitle },
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            dialogTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.action_close),
                                tint = NetflixLightGray,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Search bar
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 14.sp,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(searchFocusRequester),
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (query.isEmpty()) {
                                            Text(
                                                stringResource(R.string.player_subtitle_search_hint),
                                                color = Color.White.copy(alpha = 0.4f),
                                                fontSize = 14.sp,
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                            // Search button
                            val searchInteraction = remember { MutableInteractionSource() }
                            val isSearchFocused by searchInteraction.collectIsFocusedAsState()
                            Surface(
                                onClick = {
                                    if (query.isNotBlank() && !isSearching) {
                                        scope.launch {
                                            isSearching = true
                                            errorMessage = null
                                            val result = subtitleSearchService.search(
                                                query = query,
                                                seasonNumber = seasonNumber,
                                                episodeNumber = episodeNumber,
                                            )
                                            result.onSuccess {
                                                results = it
                                            }.onFailure {
                                                errorMessage = it.message
                                            }
                                            isSearching = false
                                            hasSearched = true
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSearchFocused) Color.White else MaterialTheme.colorScheme.primary,
                                interactionSource = searchInteraction,
                                modifier = Modifier.height(32.dp),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                ) {
                                    Text(
                                        "Search",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSearchFocused) Color.Black else Color.White,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Content area
                    when {
                        isSearching -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        stringResource(R.string.player_subtitle_searching),
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        }
                        isDownloading -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        stringResource(R.string.player_subtitle_downloading),
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        }
                        errorMessage != null -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    errorMessage ?: "",
                                    color = Color(0xFFEF5350),
                                    fontSize = 14.sp,
                                )
                            }
                        }
                        hasSearched && results.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    stringResource(R.string.player_subtitle_no_results),
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 14.sp,
                                )
                            }
                        }
                        else -> {
                            LazyColumn {
                                itemsIndexed(results) { index, result ->
                                    val fr = remember { FocusRequester() }
                                    if (index == 0) {
                                        LaunchedEffect(Unit) { fr.requestFocus() }
                                    }
                                    SubtitleResultItem(
                                        result = result,
                                        onClick = {
                                            scope.launch {
                                                isDownloading = true
                                                errorMessage = null
                                                val downloadResult = subtitleSearchService.download(result.fileId)
                                                downloadResult.onSuccess { file ->
                                                    onSubtitleDownloaded(file.absolutePath)
                                                }.onFailure {
                                                    errorMessage = it.message
                                                }
                                                isDownloading = false
                                            }
                                        },
                                        modifier = if (index == 0) Modifier.focusRequester(fr) else Modifier,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleResultItem(
    result: SubtitleSearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = when {
            isFocused -> Color.White
            else -> Color.Transparent
        },
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.release,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isFocused) Color.Black else Color.White,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Language badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isFocused) Color.Black.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    ) {
                        Text(
                            text = result.language.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isFocused) Color.Black else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    // Download count
                    Text(
                        text = stringResource(R.string.player_subtitle_downloads_label, result.downloadCount),
                        fontSize = 11.sp,
                        color = if (isFocused) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.5f),
                    )
                    // HI badge
                    if (result.hearingImpaired) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isFocused) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.1f),
                        ) {
                            Text(
                                text = stringResource(R.string.player_subtitle_hi_label),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFocused) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

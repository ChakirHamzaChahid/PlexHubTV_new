package com.chakir.plexhubtv.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.core.model.SourceType

/**
 * Server filter dialog — Netflix dark style.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServerFilterDialog(
    availableServers: List<String>,
    selectedServer: String?,
    onDismiss: () -> Unit,
    onApply: (String?) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val cs = MaterialTheme.colorScheme

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(cs.background.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.5f),
                shape = RoundedCornerShape(16.dp),
                color = cs.surface,
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    DialogHeader(
                        title = stringResource(R.string.filter_by_server),
                        onDismiss = onDismiss,
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    if (availableServers.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SelectableChip(
                                text = stringResource(R.string.filter_all),
                                isSelected = selectedServer == null,
                                onClick = { onApply(null) },
                                modifier = Modifier.focusRequester(focusRequester),
                            )
                            availableServers.forEach { server ->
                                SelectableChip(
                                    text = server,
                                    isSelected = selectedServer == server,
                                    onClick = {
                                        onApply(if (selectedServer == server) null else server)
                                    },
                                )
                            }
                        }
                    } else {
                        Text(
                            stringResource(R.string.filter_no_servers),
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Genre filter dialog — Netflix dark style.
 * Filters out "All"/"Tout" from API data to avoid redundancy with the explicit chip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreFilterDialog(
    availableGenres: List<String>,
    selectedGenre: String?,
    onDismiss: () -> Unit,
    onApply: (String?) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val filteredGenres = remember(availableGenres) {
        val excludedGenres = setOf("all", "tout", "tous", "toutes")
        availableGenres.filter { it.lowercase() !in excludedGenres }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val cs = MaterialTheme.colorScheme

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(cs.background.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.5f),
                shape = RoundedCornerShape(16.dp),
                color = cs.surface,
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    DialogHeader(
                        title = stringResource(R.string.filter_by_genre),
                        onDismiss = onDismiss,
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    if (filteredGenres.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val allVariants = setOf("all", "tout", "tous", "toutes")
                            SelectableChip(
                                text = stringResource(R.string.filter_all),
                                isSelected = selectedGenre == null
                                        || selectedGenre.lowercase() in allVariants,
                                onClick = { onApply(null) },
                                modifier = Modifier.focusRequester(focusRequester),
                            )
                            filteredGenres.forEach { genre ->
                                SelectableChip(
                                    text = genre,
                                    isSelected = selectedGenre == genre,
                                    onClick = {
                                        onApply(if (selectedGenre == genre) null else genre)
                                    },
                                )
                            }
                        }
                    } else {
                        Text(
                            stringResource(R.string.filter_no_genres),
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sort dialog — Netflix dark style with list items.
 */
@Composable
fun SortDialog(
    currentSort: String,
    isDescending: Boolean,
    onDismiss: () -> Unit,
    onSelectSort: (String, Boolean) -> Unit,
) {
    val options = listOf("Date Added", "Title", "Year", "Rating")
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val cs = MaterialTheme.colorScheme

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(cs.background.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.35f),
                shape = RoundedCornerShape(16.dp),
                color = cs.surface,
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    DialogHeader(
                        title = stringResource(R.string.filter_sort_by),
                        onDismiss = onDismiss,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    options.forEachIndexed { index, option ->
                        val isCurrent = currentSort == option
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()

                        val baseModifier = if (index == 0) {
                            Modifier.focusRequester(focusRequester)
                        } else {
                            Modifier
                        }

                        Row(
                            modifier = baseModifier
                                .fillMaxWidth()
                                .background(
                                    when {
                                        isFocused -> cs.onBackground
                                        isCurrent -> cs.onBackground.copy(alpha = 0.08f)
                                        else -> Color.Transparent
                                    },
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                ) {
                                    val defaultDesc = option != "Title"
                                    val newDesc =
                                        if (isCurrent) !isDescending else defaultDesc
                                    onSelectSort(option, newDesc)
                                }
                                .padding(vertical = 14.dp, horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                option,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isFocused) cs.background else cs.onBackground,
                            )
                            if (isCurrent) {
                                Text(
                                    if (isDescending) "↓" else "↑",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFocused) cs.background else cs.onBackground,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Source type filter dialog — multi-select toggle chips.
 * Each source type (Plex, Jellyfin, Xtream, Backend) can be toggled on/off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourceFilterDialog(
    availableSourceTypes: Set<SourceType>,
    excludedSourceTypes: Set<SourceType>,
    onDismiss: () -> Unit,
    onToggle: (SourceType) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val cs = MaterialTheme.colorScheme

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(cs.background.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.5f),
                shape = RoundedCornerShape(16.dp),
                color = cs.surface,
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    DialogHeader(
                        title = stringResource(R.string.filter_by_source),
                        onDismiss = onDismiss,
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    if (availableSourceTypes.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            availableSourceTypes.forEachIndexed { index, sourceType ->
                                val isEnabled = sourceType !in excludedSourceTypes
                                SelectableChip(
                                    text = sourceType.label,
                                    isSelected = isEnabled,
                                    onClick = { onToggle(sourceType) },
                                    modifier = if (index == 0) {
                                        Modifier.focusRequester(focusRequester)
                                    } else {
                                        Modifier
                                    },
                                )
                            }
                        }
                    } else {
                        Text(
                            stringResource(R.string.filter_no_sources),
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ── Shared components ──────────────────────────────────────────────

@Composable
private fun DialogHeader(
    title: String,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = cs.onBackground,
        )
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.action_close),
                tint = cs.onSurfaceVariant,
            )
        }
    }
}

/**
 * Selectable chip with Netflix-style focus: white bg + black text when focused.
 */
@Composable
private fun SelectableChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = when {
            isFocused -> cs.onBackground
            isSelected -> cs.onBackground.copy(alpha = 0.15f)
            else -> cs.onBackground.copy(alpha = 0.05f)
        },
        interactionSource = interactionSource,
        modifier = modifier,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isFocused -> cs.background
                isSelected -> cs.onBackground
                else -> cs.onSurfaceVariant
            },
        )
    }
}

package com.chakir.plexhubtv.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.focusable
import androidx.compose.ui.unit.dp

/**
 * Dialogue pour filtrer par Serveur.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServerFilterDialog(
    availableServers: List<String>,
    selectedServer: String?,
    onDismiss: () -> Unit,
    onApply: (String?) -> Unit,
) {
    // No local state needed for auto-apply

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter by Server") },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp) // Limit height for large lists
                        .verticalScroll(rememberScrollState()),
            ) {
                if (availableServers.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = selectedServer == null,
                            onClick = {
                                onApply(null) // Immediate apply
                            },
                            label = { Text("All") },
                        )
                        availableServers.forEach { server ->
                            FilterChip(
                                selected = selectedServer == server,
                                onClick = {
                                    val newSelection = if (selectedServer == server) null else server
                                    onApply(newSelection) // Immediate apply
                                },
                                label = { Text(server) },
                            )
                        }
                    }
                } else {
                    Text("No servers available")
                }
            }
        },
        confirmButton = {
            // specific confirm button removed for auto-apply UX
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
fun GenreFilterDialog(
    availableGenres: List<String>,
    selectedGenre: String?,
    onDismiss: () -> Unit,
    onApply: (String?) -> Unit,
) {
    var tempGenre by remember { mutableStateOf(selectedGenre) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter by Genre") },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
            ) {
                if (availableGenres.isNotEmpty()) {
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = selectedGenre == null || selectedGenre == "All" || selectedGenre == "Tout",
                            onClick = { onApply(null) },
                            label = { Text("All") },
                        )
                        availableGenres.filter { it != "All" && it != "Tout" }.forEach { genre ->
                            FilterChip(
                                selected = selectedGenre == genre,
                                onClick = {
                                    // Toggle logic if needed, or simple selection
                                    val newSelection = if (selectedGenre == genre) null else genre
                                    onApply(newSelection)
                                },
                                label = { Text(genre) },
                            )
                        }
                    }
                } else {
                    Text("No genres available")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
fun SortDialog(
    currentSort: String,
    isDescending: Boolean,
    onDismiss: () -> Unit,
    onSelectSort: (String, Boolean) -> Unit,
) {
    val options = listOf("Date Added", "Title", "Year", "Rating")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort By") },
        text = {
            Column {
                options.forEachIndexed { index, option ->
                    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val isFocused by interactionSource.collectIsFocusedAsState()

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isFocused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f) else Color.Transparent,
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                ) {
                                    // Toggle desc if same option clicked, else default to asc/desc based on logic?
                                    // Default logic: Date -> Desc default, Title -> Asc default
                                    val defaultDesc = option == "Date Added" || option == "Year" || option == "Rating"
                                    val newDesc = if (currentSort == option) !isDescending else defaultDesc
                                    onSelectSort(option, newDesc)
                                }
                                .focusable(interactionSource = interactionSource)
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(option)
                        if (currentSort == option) {
                            Text(
                                if (isDescending) "↓" else "↑",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

package com.chakir.plexhubtv.feature.libraryselection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LibrarySelectionRoute(
    viewModel: LibrarySelectionViewModel = hiltViewModel(),
    onNavigateToLoading: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                LibrarySelectionNavigationEvent.NavigateToLoading -> onNavigateToLoading()
            }
        }
    }

    LibrarySelectionScreen(
        state = uiState,
        onAction = viewModel::onAction,
    )
}

@Composable
fun LibrarySelectionScreen(
    state: LibrarySelectionUiState,
    onAction: (LibrarySelectionAction) -> Unit,
) {
    val selectedCount = state.servers.sumOf { server ->
        server.libraries.count { it.isSelected }
    }
    val totalCount = state.servers.sumOf { it.libraries.size }
    val confirmFocusRequester = remember { FocusRequester() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            // Header
            Text(
                text = "Choisissez vos bibliothèques",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sélectionnez les bibliothèques que vous souhaitez synchroniser",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Chargement des serveurs...",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }

                state.error != null -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(64.dp),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = state.error,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = { onAction(LibrarySelectionAction.Retry) }) {
                                Text("Réessayer")
                            }
                        }
                    }
                }

                else -> {
                    // Server/Library list
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        state.servers.forEach { server ->
                            // Server header
                            item(key = "header_${server.serverId}") {
                                ServerHeader(
                                    server = server,
                                    onToggleAll = { onAction(LibrarySelectionAction.ToggleServer(server.serverId)) },
                                )
                            }

                            // Libraries
                            items(
                                items = server.libraries,
                                key = { "${server.serverId}:${it.key}" },
                            ) { library ->
                                LibraryItem(
                                    library = library,
                                    onClick = {
                                        onAction(LibrarySelectionAction.ToggleLibrary(server.serverId, library.key))
                                    },
                                )
                            }
                        }

                        // Bottom spacing for the confirm button
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    // Confirm button
                    Spacer(modifier = Modifier.height(16.dp))
                    val confirmInteractionSource = remember { MutableInteractionSource() }
                    val isConfirmFocused by confirmInteractionSource.collectIsFocusedAsState()
                    Button(
                        onClick = { onAction(LibrarySelectionAction.Confirm) },
                        enabled = selectedCount > 0 && !state.isConfirming,
                        interactionSource = confirmInteractionSource,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConfirmFocused) Color(0xFF00C853) else MaterialTheme.colorScheme.primary,
                            contentColor = if (isConfirmFocused) Color.White else MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .focusRequester(confirmFocusRequester)
                            .scale(if (isConfirmFocused) 1.03f else 1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (state.isConfirming) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                text = if (selectedCount > 0) {
                                    "Confirmer ($selectedCount/$totalCount)"
                                } else {
                                    "Sélectionnez au moins une bibliothèque"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerHeader(
    server: ServerWithLibraries,
    onToggleAll: () -> Unit,
) {
    val allSelected = server.libraries.all { it.isSelected }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .clickable(onClick = onToggleAll)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Dns,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = server.serverName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Checkbox(
            checked = allSelected,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun LibraryItem(
    library: SelectableLibrary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (library.type == "movie") Icons.Default.Movie else Icons.Default.LiveTv,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = library.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (library.type == "movie") "Films" else "Séries",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Checkbox(
            checked = library.isSelected,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

package com.chakir.plexhubtv.feature.xtream

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
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.R

@Composable
fun XtreamCategorySelectionRoute(
    accountId: String,
    viewModel: XtreamCategorySelectionViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                XtreamCategorySelectionNavEvent.NavigateBack -> onNavigateBack()
            }
        }
    }

    XtreamCategorySelectionScreen(
        state = uiState,
        onAction = viewModel::onAction,
    )
}

@Composable
fun XtreamCategorySelectionScreen(
    state: XtreamCategorySelectionUiState,
    onAction: (XtreamCategorySelectionAction) -> Unit,
) {
    val selectedCount = state.sections.sumOf { section ->
        section.vodCategories.count { it.isSelected } +
            section.seriesCategories.count { it.isSelected }
    }
    val totalCount = state.sections.sumOf {
        it.vodCategories.size + it.seriesCategories.size
    }
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
                text = stringResource(R.string.xtream_category_header),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.xtream_category_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Filter mode indicator chip
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(
                        if (state.isUnifiedMode) R.string.xtream_filter_unified
                        else R.string.xtream_filter_non_unified
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                                text = stringResource(R.string.xtream_category_loading),
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
                            Button(onClick = { onAction(XtreamCategorySelectionAction.Retry) }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.sections.forEach { section ->
                            // Account header
                            item(key = "account_${section.accountId}") {
                                AccountHeader(section = section)
                            }

                            // VOD sub-header
                            if (section.vodCategories.isNotEmpty()) {
                                item(key = "vod_header_${section.accountId}") {
                                    SubSectionHeader(
                                        title = stringResource(R.string.xtream_category_vod),
                                        icon = Icons.Default.Movie,
                                        allSelected = section.vodCategories.all { it.isSelected },
                                        onToggleAll = {
                                            onAction(XtreamCategorySelectionAction.ToggleAllVod(section.accountId))
                                        },
                                    )
                                }

                                items(
                                    items = section.vodCategories,
                                    key = { "${section.accountId}:vod:${it.categoryId}" },
                                ) { category ->
                                    CategoryItem(
                                        category = category,
                                        onClick = {
                                            onAction(
                                                XtreamCategorySelectionAction.ToggleVodCategory(
                                                    section.accountId,
                                                    category.categoryId,
                                                ),
                                            )
                                        },
                                    )
                                }
                            }

                            // Series sub-header
                            if (section.seriesCategories.isNotEmpty()) {
                                item(key = "series_header_${section.accountId}") {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    SubSectionHeader(
                                        title = stringResource(R.string.xtream_category_series),
                                        icon = Icons.Default.Tv,
                                        allSelected = section.seriesCategories.all { it.isSelected },
                                        onToggleAll = {
                                            onAction(XtreamCategorySelectionAction.ToggleAllSeries(section.accountId))
                                        },
                                    )
                                }

                                items(
                                    items = section.seriesCategories,
                                    key = { "${section.accountId}:series:${it.categoryId}" },
                                ) { category ->
                                    CategoryItem(
                                        category = category,
                                        onClick = {
                                            onAction(
                                                XtreamCategorySelectionAction.ToggleSeriesCategory(
                                                    section.accountId,
                                                    category.categoryId,
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    // Confirm button
                    Spacer(modifier = Modifier.height(16.dp))
                    val confirmInteractionSource = remember { MutableInteractionSource() }
                    val isConfirmFocused by confirmInteractionSource.collectIsFocusedAsState()
                    Button(
                        onClick = { onAction(XtreamCategorySelectionAction.Confirm) },
                        enabled = selectedCount > 0 && !state.isConfirming && !state.isSyncing,
                        interactionSource = confirmInteractionSource,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConfirmFocused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            contentColor = if (isConfirmFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary,
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
                                    stringResource(R.string.xtream_category_confirm, selectedCount, totalCount)
                                } else {
                                    stringResource(R.string.xtream_category_select_one)
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
private fun AccountHeader(section: CategorySection) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.LiveTv,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = section.accountLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SubSectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    allSelected: Boolean,
    onToggleAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onToggleAll)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
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
private fun CategoryItem(
    category: SelectableCategory,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = category.categoryName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Checkbox(
            checked = category.isSelected,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

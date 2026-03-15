package com.chakir.plexhubtv.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chakir.plexhubtv.R

/**
 * Grid of setting category cards replacing the old monolithic settings list.
 * 2 rows: first row 4 cards, second row 3 cards.
 */
@Composable
fun SettingsGridScreen(
    appVersion: String,
    onCategorySelected: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstCardFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { firstCardFocusRequester.requestFocus() } catch (_: Exception) { }
    }

    val topRow = listOf(
        SettingsCategory.General,
        SettingsCategory.Playback,
        SettingsCategory.Server,
        SettingsCategory.DataSync,
    )
    val bottomRow = listOf(
        SettingsCategory.Services,
        SettingsCategory.System,
        SettingsCategory.Account,
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                topRow.forEachIndexed { index, category ->
                    val cardModifier = if (index == 0) {
                        Modifier
                            .weight(1f)
                            .height(160.dp)
                            .focusRequester(firstCardFocusRequester)
                    } else {
                        Modifier
                            .weight(1f)
                            .height(160.dp)
                    }
                    SettingsCategoryCard(
                        icon = category.icon,
                        title = stringResource(category.titleRes),
                        subtitle = stringResource(category.subtitleRes),
                        onClick = { onCategorySelected(category) },
                        modifier = cardModifier,
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                bottomRow.forEach { category ->
                    SettingsCategoryCard(
                        icon = category.icon,
                        title = stringResource(category.titleRes),
                        subtitle = stringResource(category.subtitleRes),
                        onClick = { onCategorySelected(category) },
                        modifier = Modifier
                            .weight(1f)
                            .height(160.dp),
                    )
                }
                // Empty spacer to balance with 4-column row
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.settings_version, appVersion),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

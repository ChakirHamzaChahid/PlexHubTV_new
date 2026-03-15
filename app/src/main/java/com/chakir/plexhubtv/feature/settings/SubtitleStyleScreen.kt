package com.chakir.plexhubtv.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.core.model.SubtitlePreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleStyleRoute(
    viewModel: SubtitleStyleViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val prefs by viewModel.prefs.collectAsState()

    SubtitleStyleScreen(
        prefs = prefs,
        onFontSizeChange = viewModel::setFontSize,
        onFontColorChange = viewModel::setFontColor,
        onBgColorChange = viewModel::setBgColor,
        onEdgeTypeChange = viewModel::setEdgeType,
        onNavigateBack = onNavigateBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleStyleScreen(
    prefs: SubtitlePreferences,
    onFontSizeChange: (Int) -> Unit,
    onFontColorChange: (Long) -> Unit,
    onBgColorChange: (Long) -> Unit,
    onEdgeTypeChange: (Int) -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.padding(top = 56.dp),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_subtitle_style), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back_description))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Live Preview
            item {
                SubtitlePreview(prefs)
            }

            // Font Size
            item {
                val fontSizes = listOf(16, 18, 20, 22, 24, 28, 32)
                var showDialog by remember { mutableStateOf(false) }

                SettingsTile(
                    title = stringResource(R.string.settings_subtitle_font_size),
                    subtitle = "${prefs.fontSize}sp",
                    onClick = { showDialog = true },
                )

                if (showDialog) {
                    SettingsDialog(
                        title = stringResource(R.string.settings_subtitle_font_size),
                        options = fontSizes.map { "${it}sp" },
                        currentValue = "${prefs.fontSize}sp",
                        onDismissRequest = { showDialog = false },
                        onOptionSelected = { selected ->
                            val size = selected.removeSuffix("sp").toIntOrNull() ?: 22
                            onFontSizeChange(size)
                            showDialog = false
                        },
                    )
                }
            }

            // Font Color
            item {
                val colorOptions = listOf(
                    stringResource(R.string.settings_color_white) to 0xFFFFFFFF,
                    stringResource(R.string.settings_color_yellow) to 0xFFFFFF00,
                    stringResource(R.string.settings_color_green) to 0xFF00FF00,
                    stringResource(R.string.settings_color_cyan) to 0xFF00FFFF,
                    stringResource(R.string.settings_color_blue) to 0xFF0000FF,
                )
                var showDialog by remember { mutableStateOf(false) }

                SettingsTile(
                    title = stringResource(R.string.settings_subtitle_font_color),
                    subtitle = colorOptions.find { it.second == prefs.fontColor }?.first
                        ?: stringResource(R.string.settings_color_white),
                    onClick = { showDialog = true },
                )

                if (showDialog) {
                    SettingsDialog(
                        title = stringResource(R.string.settings_subtitle_font_color),
                        options = colorOptions.map { it.first },
                        currentValue = colorOptions.find { it.second == prefs.fontColor }?.first
                            ?: colorOptions.first().first,
                        onDismissRequest = { showDialog = false },
                        onOptionSelected = { selected ->
                            val color = colorOptions.find { it.first == selected }?.second ?: 0xFFFFFFFF
                            onFontColorChange(color)
                            showDialog = false
                        },
                    )
                }
            }

            // Background Color
            item {
                val bgOptions = listOf(
                    stringResource(R.string.settings_bg_transparent) to 0x00000000L,
                    stringResource(R.string.settings_bg_semi_transparent) to 0x80000000L,
                    stringResource(R.string.settings_bg_black) to 0xFF000000L,
                )
                var showDialog by remember { mutableStateOf(false) }

                SettingsTile(
                    title = stringResource(R.string.settings_subtitle_bg_color),
                    subtitle = bgOptions.find { it.second == prefs.backgroundColor }?.first
                        ?: stringResource(R.string.settings_bg_semi_transparent),
                    onClick = { showDialog = true },
                )

                if (showDialog) {
                    SettingsDialog(
                        title = stringResource(R.string.settings_subtitle_bg_color),
                        options = bgOptions.map { it.first },
                        currentValue = bgOptions.find { it.second == prefs.backgroundColor }?.first
                            ?: bgOptions[1].first,
                        onDismissRequest = { showDialog = false },
                        onOptionSelected = { selected ->
                            val color = bgOptions.find { it.first == selected }?.second ?: 0x80000000L
                            onBgColorChange(color)
                            showDialog = false
                        },
                    )
                }
            }

            // Edge Type
            item {
                val edgeOptions = listOf(
                    stringResource(R.string.settings_edge_none) to 0,
                    stringResource(R.string.settings_edge_outline) to 1,
                    stringResource(R.string.settings_edge_drop_shadow) to 2,
                    stringResource(R.string.settings_edge_raised) to 3,
                    stringResource(R.string.settings_edge_depressed) to 4,
                )
                var showDialog by remember { mutableStateOf(false) }

                SettingsTile(
                    title = stringResource(R.string.settings_subtitle_edge_type),
                    subtitle = edgeOptions.find { it.second == prefs.edgeType }?.first
                        ?: stringResource(R.string.settings_edge_none),
                    onClick = { showDialog = true },
                )

                if (showDialog) {
                    SettingsDialog(
                        title = stringResource(R.string.settings_subtitle_edge_type),
                        options = edgeOptions.map { it.first },
                        currentValue = edgeOptions.find { it.second == prefs.edgeType }?.first
                            ?: edgeOptions.first().first,
                        onDismissRequest = { showDialog = false },
                        onOptionSelected = { selected ->
                            val type = edgeOptions.find { it.first == selected }?.second ?: 0
                            onEdgeTypeChange(type)
                            showDialog = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SubtitlePreview(prefs: SubtitlePreferences) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 24.dp)
                .background(Color(prefs.backgroundColor))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                text = "Sample Subtitle Text",
                color = Color(prefs.fontColor),
                fontSize = prefs.fontSize.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
        }
    }
}

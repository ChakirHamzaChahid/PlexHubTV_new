package com.chakir.plexhubtv.feature.settings.categories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.feature.settings.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onNavigateBack: () -> Unit,
) {
    var showQualityDialog by remember { mutableStateOf(false) }
    var showPlayerEngineDialog by remember { mutableStateOf(false) }
    var showDeinterlaceDialog by remember { mutableStateOf(false) }
    var showSkipIntroDialog by remember { mutableStateOf(false) }
    var showSkipCreditsDialog by remember { mutableStateOf(false) }
    var showAudioLangDialog by remember { mutableStateOf(false) }
    var showSubtitleLangDialog by remember { mutableStateOf(false) }
    val listFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { listFocusRequester.requestFocus() } catch (_: Exception) { }
    }

    Scaffold(
        modifier = Modifier.padding(top = 56.dp),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_category_playback), fontWeight = FontWeight.Bold) },
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
                .fillMaxSize()
                .focusRequester(listFocusRequester),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // --- Playback ---
            item {
                SettingsSection(stringResource(R.string.settings_section_playback)) {
                    SettingsTile(
                        title = stringResource(R.string.settings_video_quality),
                        subtitle = state.videoQuality,
                        onClick = { showQualityDialog = true },
                    )
                    SettingsTile(
                        title = stringResource(R.string.settings_player_engine),
                        subtitle = state.playerEngine,
                        onClick = { showPlayerEngineDialog = true },
                    )
                    SettingsTile(
                        title = stringResource(R.string.settings_deinterlace_mode),
                        subtitle = when (state.deinterlaceMode) {
                            "auto" -> stringResource(R.string.settings_deinterlace_auto)
                            "off" -> stringResource(R.string.settings_deinterlace_off)
                            else -> state.deinterlaceMode
                        },
                        onClick = { showDeinterlaceDialog = true },
                    )
                    SettingsSwitch(
                        title = stringResource(R.string.settings_auto_play_next),
                        subtitle = stringResource(R.string.settings_auto_play_next_subtitle),
                        isChecked = state.autoPlayNextEnabled,
                        onCheckedChange = { onAction(SettingsAction.ToggleAutoPlayNext(it)) },
                    )
                    SettingsTile(
                        title = stringResource(R.string.settings_skip_intro),
                        subtitle = when (state.skipIntroMode) {
                            "auto" -> stringResource(R.string.settings_skip_mode_auto)
                            "ask" -> stringResource(R.string.settings_skip_mode_ask)
                            else -> stringResource(R.string.settings_skip_mode_off)
                        },
                        onClick = { showSkipIntroDialog = true },
                    )
                    SettingsTile(
                        title = stringResource(R.string.settings_skip_credits),
                        subtitle = when (state.skipCreditsMode) {
                            "auto" -> stringResource(R.string.settings_skip_mode_auto)
                            "ask" -> stringResource(R.string.settings_skip_mode_ask)
                            else -> stringResource(R.string.settings_skip_mode_off)
                        },
                        onClick = { showSkipCreditsDialog = true },
                    )
                    SettingsSwitch(
                        title = stringResource(R.string.settings_theme_song),
                        subtitle = stringResource(R.string.settings_theme_song_subtitle),
                        isChecked = state.themeSongEnabled,
                        onCheckedChange = { onAction(SettingsAction.ToggleThemeSong(it)) },
                    )
                }
            }

            // --- Languages ---
            item {
                SettingsSection(stringResource(R.string.settings_section_languages)) {
                    val audioOptions = getAudioLanguageOptions()
                    val currentAudioDisplay =
                        audioOptions.find { it.second == state.preferredAudioLanguage }?.first
                            ?: state.preferredAudioLanguage ?: stringResource(R.string.settings_lang_original)

                    SettingsTile(
                        title = stringResource(R.string.settings_preferred_audio),
                        subtitle = currentAudioDisplay,
                        onClick = { showAudioLangDialog = true },
                    )

                    val subtitleOptions = getSubtitleLanguageOptions()
                    val currentSubtitleDisplay =
                        subtitleOptions.find { it.second == state.preferredSubtitleLanguage }?.first
                            ?: state.preferredSubtitleLanguage ?: stringResource(R.string.settings_lang_none)

                    SettingsTile(
                        title = stringResource(R.string.settings_preferred_subtitle),
                        subtitle = currentSubtitleDisplay,
                        onClick = { showSubtitleLangDialog = true },
                    )
                    SettingsTile(
                        title = stringResource(R.string.settings_subtitle_style),
                        subtitle = stringResource(R.string.settings_subtitle_style_subtitle),
                        onClick = { onAction(SettingsAction.NavigateToSubtitleStyle) },
                    )
                }
            }
        }
    }

    // --- Dialogs ---
    if (showQualityDialog) {
        val options = listOf(
            stringResource(R.string.settings_quality_original),
            stringResource(R.string.settings_quality_1080p_20),
            stringResource(R.string.settings_quality_1080p_12),
            stringResource(R.string.settings_quality_1080p_8),
            stringResource(R.string.settings_quality_720p_4),
            stringResource(R.string.settings_quality_720p_3),
        )
        SettingsDialog(
            title = stringResource(R.string.settings_video_quality),
            options = options,
            currentValue = state.videoQuality,
            onDismissRequest = { showQualityDialog = false },
            onOptionSelected = {
                onAction(SettingsAction.ChangeVideoQuality(it))
                showQualityDialog = false
            },
        )
    }

    if (showPlayerEngineDialog) {
        val options = listOf(stringResource(R.string.settings_player_exoplayer), stringResource(R.string.settings_player_mpv))
        SettingsDialog(
            title = stringResource(R.string.settings_player_engine),
            options = options,
            currentValue = state.playerEngine,
            onDismissRequest = { showPlayerEngineDialog = false },
            onOptionSelected = {
                onAction(SettingsAction.ChangePlayerEngine(it))
                showPlayerEngineDialog = false
            },
        )
    }

    if (showDeinterlaceDialog) {
        val options = listOf(
            stringResource(R.string.settings_deinterlace_auto),
            stringResource(R.string.settings_deinterlace_off),
        )
        SettingsDialog(
            title = stringResource(R.string.settings_deinterlace_mode),
            options = options,
            currentValue = when (state.deinterlaceMode) {
                "auto" -> stringResource(R.string.settings_deinterlace_auto)
                "off" -> stringResource(R.string.settings_deinterlace_off)
                else -> state.deinterlaceMode
            },
            onDismissRequest = { showDeinterlaceDialog = false },
            onOptionSelected = { selected ->
                val mode = when (selected) {
                    options[0] -> "auto"
                    else -> "off"
                }
                onAction(SettingsAction.ChangeDeinterlaceMode(mode))
                showDeinterlaceDialog = false
            },
        )
    }

    if (showSkipIntroDialog) {
        val optAuto = stringResource(R.string.settings_skip_mode_auto)
        val optAsk = stringResource(R.string.settings_skip_mode_ask)
        val optOff = stringResource(R.string.settings_skip_mode_off)
        SettingsDialog(
            title = stringResource(R.string.settings_skip_intro),
            options = listOf(optAuto, optAsk, optOff),
            currentValue = when (state.skipIntroMode) {
                "auto" -> optAuto; "ask" -> optAsk; else -> optOff
            },
            onDismissRequest = { showSkipIntroDialog = false },
            onOptionSelected = { selected ->
                val mode = when (selected) { optAuto -> "auto"; optAsk -> "ask"; else -> "off" }
                onAction(SettingsAction.ChangeSkipIntroMode(mode))
                showSkipIntroDialog = false
            },
        )
    }

    if (showSkipCreditsDialog) {
        val optAuto = stringResource(R.string.settings_skip_mode_auto)
        val optAsk = stringResource(R.string.settings_skip_mode_ask)
        val optOff = stringResource(R.string.settings_skip_mode_off)
        SettingsDialog(
            title = stringResource(R.string.settings_skip_credits),
            options = listOf(optAuto, optAsk, optOff),
            currentValue = when (state.skipCreditsMode) {
                "auto" -> optAuto; "ask" -> optAsk; else -> optOff
            },
            onDismissRequest = { showSkipCreditsDialog = false },
            onOptionSelected = { selected ->
                val mode = when (selected) { optAuto -> "auto"; optAsk -> "ask"; else -> "off" }
                onAction(SettingsAction.ChangeSkipCreditsMode(mode))
                showSkipCreditsDialog = false
            },
        )
    }

    if (showAudioLangDialog) {
        val audioOptions = getAudioLanguageOptions()
        SettingsDialog(
            title = stringResource(R.string.settings_preferred_audio),
            options = audioOptions.map { it.first },
            currentValue = audioOptions.find { it.second == state.preferredAudioLanguage }?.first
                ?: stringResource(R.string.settings_lang_original),
            onDismissRequest = { showAudioLangDialog = false },
            onOptionSelected = { selectedName ->
                val isoCode = audioOptions.find { it.first == selectedName }?.second
                onAction(SettingsAction.ChangePreferredAudioLanguage(isoCode))
                showAudioLangDialog = false
            },
        )
    }

    if (showSubtitleLangDialog) {
        val subtitleOptions = getSubtitleLanguageOptions()
        SettingsDialog(
            title = stringResource(R.string.settings_preferred_subtitle),
            options = subtitleOptions.map { it.first },
            currentValue = subtitleOptions.find { it.second == state.preferredSubtitleLanguage }?.first
                ?: stringResource(R.string.settings_lang_none),
            onDismissRequest = { showSubtitleLangDialog = false },
            onOptionSelected = { selectedName ->
                val isoCode = subtitleOptions.find { it.first == selectedName }?.second
                onAction(SettingsAction.ChangePreferredSubtitleLanguage(isoCode))
                showSubtitleLangDialog = false
            },
        )
    }
}

@Composable
internal fun getAudioLanguageOptions() =
    listOf(
        stringResource(R.string.settings_lang_original) to null,
        stringResource(R.string.settings_lang_english) to "eng",
        stringResource(R.string.settings_lang_french) to "fra",
        stringResource(R.string.settings_lang_german) to "deu",
        stringResource(R.string.settings_lang_spanish) to "spa",
        stringResource(R.string.settings_lang_italian) to "ita",
        stringResource(R.string.settings_lang_japanese) to "jpn",
        stringResource(R.string.settings_lang_korean) to "kor",
        stringResource(R.string.settings_lang_russian) to "rus",
        stringResource(R.string.settings_lang_portuguese) to "por",
    )

@Composable
internal fun getSubtitleLanguageOptions() =
    listOf(
        stringResource(R.string.settings_lang_none) to null,
        stringResource(R.string.settings_lang_english) to "eng",
        stringResource(R.string.settings_lang_french) to "fra",
        stringResource(R.string.settings_lang_german) to "deu",
        stringResource(R.string.settings_lang_spanish) to "spa",
        stringResource(R.string.settings_lang_italian) to "ita",
        stringResource(R.string.settings_lang_japanese) to "jpn",
        stringResource(R.string.settings_lang_korean) to "kor",
        stringResource(R.string.settings_lang_russian) to "rus",
        stringResource(R.string.settings_lang_portuguese) to "por",
    )

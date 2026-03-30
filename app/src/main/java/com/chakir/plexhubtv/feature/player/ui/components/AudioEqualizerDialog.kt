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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.feature.player.controller.EqualizerState

@Composable
fun AudioEqualizerDialog(
    state: EqualizerState,
    onPresetSelected: (Int) -> Unit,
    onBandChanged: (bandIndex: Int, level: Int) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val title = stringResource(R.string.player_equalizer_title)

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
                shape = RoundedCornerShape(16.dp),
                color = cs.surface,
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .heightIn(max = 520.dp)
                    .testTag("dialog_equalizer")
                    .semantics { contentDescription = title },
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    // Header with enable toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = cs.onBackground,
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Switch(
                                checked = state.enabled,
                                onCheckedChange = onEnabledChanged,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    uncheckedThumbColor = cs.onBackground.copy(alpha = 0.6f),
                                    uncheckedTrackColor = cs.onBackground.copy(alpha = 0.1f),
                                ),
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.action_close),
                                tint = cs.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Presets row
                    Text(
                        text = stringResource(R.string.player_equalizer_presets),
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    val firstPresetFr = remember { FocusRequester() }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(state.presets) { index, preset ->
                            val interactionSource = remember { MutableInteractionSource() }
                            val isFocused by interactionSource.collectIsFocusedAsState()
                            val isSelected = state.selectedPresetIndex == index

                            Surface(
                                onClick = { onPresetSelected(index) },
                                shape = RoundedCornerShape(8.dp),
                                color = when {
                                    isFocused -> cs.onBackground
                                    isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    else -> cs.onBackground.copy(alpha = 0.08f)
                                },
                                interactionSource = interactionSource,
                                modifier = Modifier
                                    .height(36.dp)
                                    .then(if (index == 0) Modifier.focusRequester(firstPresetFr) else Modifier),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                ) {
                                    Text(
                                        text = preset.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isFocused -> cs.background
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            else -> cs.onBackground
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Band sliders
                    if (state.bandLevels.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.player_equalizer_bands),
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onBackground.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 8.dp),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            state.bandLevels.forEachIndexed { bandIndex, level ->
                                val freq = state.bandFrequencies.getOrNull(bandIndex) ?: 0
                                BandColumn(
                                    frequency = freq,
                                    level = level,
                                    minLevel = state.minLevel,
                                    maxLevel = state.maxLevel,
                                    enabled = state.enabled,
                                    onLevelChanged = { onBandChanged(bandIndex, it) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BandColumn(
    frequency: Int,
    level: Int,
    minLevel: Int,
    maxLevel: Int,
    enabled: Boolean,
    onLevelChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val freqLabel = if (frequency >= 1_000_000) {
        "${frequency / 1_000_000}kHz"
    } else {
        "${frequency / 1000}Hz"
    }

    val step = 100 // 100 millibels per step
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 2.dp),
    ) {
        // Level indicator
        val dbLabel = if (level >= 0) "+${level / 100.0}dB" else "${level / 100.0}dB"
        Text(
            text = dbLabel,
            fontSize = 10.sp,
            color = if (enabled) cs.onBackground else cs.onBackground.copy(alpha = 0.3f),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Visual bar
        val range = maxLevel - minLevel
        val normalizedLevel = if (range > 0) (level - minLevel).toFloat() / range else 0.5f

        Surface(
            onClick = {},
            shape = RoundedCornerShape(6.dp),
            color = if (isFocused) cs.onBackground else cs.onBackground.copy(alpha = 0.05f),
            interactionSource = interactionSource,
            modifier = Modifier
                .height(140.dp)
                .fillMaxWidth()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && enabled) {
                        when (event.key.nativeKeyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                onLevelChanged((level + step).coerceAtMost(maxLevel))
                                true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                onLevelChanged((level - step).coerceAtLeast(minLevel))
                                true
                            }
                            else -> false
                        }
                    } else false
                },
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                // Filled portion
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((140 * normalizedLevel).dp)
                        .background(
                            color = if (enabled) {
                                if (isFocused) cs.background.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            } else {
                                cs.onBackground.copy(alpha = 0.1f)
                            },
                            shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp),
                        ),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Frequency label
        Text(
            text = freqLabel,
            fontSize = 9.sp,
            color = cs.onBackground.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
        )
    }
}

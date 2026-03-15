package com.chakir.plexhubtv.feature.player.controller

import android.media.audiofx.Equalizer
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class EqualizerPreset(
    val name: String,
    val bands: List<Int>, // Gain in millibels for each band
)

data class EqualizerState(
    val enabled: Boolean = false,
    val selectedPresetIndex: Int = -1, // -1 = Custom
    val bandLevels: List<Int> = emptyList(), // Current band levels in millibels
    val bandFrequencies: List<Int> = emptyList(), // Center frequencies in millihertz
    val minLevel: Int = -1500,
    val maxLevel: Int = 1500,
    val presets: List<EqualizerPreset> = DEFAULT_PRESETS,
)

val DEFAULT_PRESETS = listOf(
    EqualizerPreset("Flat", listOf(0, 0, 0, 0, 0)),
    EqualizerPreset("Bass Boost", listOf(600, 400, 0, 0, 0)),
    EqualizerPreset("Rock", listOf(400, 200, -100, 200, 400)),
    EqualizerPreset("Pop", listOf(-100, 200, 400, 200, -100)),
    EqualizerPreset("Jazz", listOf(300, 0, 100, -100, 300)),
    EqualizerPreset("Classical", listOf(300, 200, -100, 200, 400)),
    EqualizerPreset("Voice", listOf(-200, 0, 300, 200, 0)),
    EqualizerPreset("Loudness", listOf(400, 200, 0, 200, 400)),
)

@Singleton
class AudioEqualizerManager @Inject constructor() {

    private var equalizer: Equalizer? = null
    private var _state = EqualizerState()
    val state: EqualizerState get() = _state

    fun attachToAudioSession(audioSessionId: Int) {
        release()
        try {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq

            val numBands = eq.numberOfBands.toInt()
            val levels = (0 until numBands).map { eq.getBandLevel(it.toShort()).toInt() }
            val frequencies = (0 until numBands).map { eq.getCenterFreq(it.toShort()) }

            // Adapt default presets to actual band count
            val adaptedPresets = DEFAULT_PRESETS.map { preset ->
                if (preset.bands.size == numBands) preset
                else preset.copy(bands = adaptBands(preset.bands, numBands))
            }

            _state = EqualizerState(
                enabled = false,
                selectedPresetIndex = -1,
                bandLevels = levels,
                bandFrequencies = frequencies,
                minLevel = eq.bandLevelRange[0].toInt(),
                maxLevel = eq.bandLevelRange[1].toInt(),
                presets = adaptedPresets,
            )
            Timber.d("Equalizer attached: $numBands bands, range ${_state.minLevel}..${_state.maxLevel}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to create Equalizer")
        }
    }

    fun setEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
        _state = _state.copy(enabled = enabled)
    }

    fun selectPreset(index: Int) {
        val preset = _state.presets.getOrNull(index) ?: return
        val eq = equalizer ?: return

        val numBands = eq.numberOfBands.toInt()
        val bands = if (preset.bands.size == numBands) preset.bands
        else adaptBands(preset.bands, numBands)

        bands.forEachIndexed { i, level ->
            val clampedLevel = level.coerceIn(_state.minLevel, _state.maxLevel)
            eq.setBandLevel(i.toShort(), clampedLevel.toShort())
        }

        if (!_state.enabled) {
            eq.enabled = true
        }

        _state = _state.copy(
            enabled = true,
            selectedPresetIndex = index,
            bandLevels = bands.map { it.coerceIn(_state.minLevel, _state.maxLevel) },
        )
    }

    fun setBandLevel(bandIndex: Int, level: Int) {
        val eq = equalizer ?: return
        val clampedLevel = level.coerceIn(_state.minLevel, _state.maxLevel)
        eq.setBandLevel(bandIndex.toShort(), clampedLevel.toShort())

        val newLevels = _state.bandLevels.toMutableList()
        newLevels[bandIndex] = clampedLevel
        _state = _state.copy(
            bandLevels = newLevels,
            selectedPresetIndex = -1, // Custom
        )
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (e: Exception) {
            Timber.w(e, "Error releasing equalizer")
        }
        equalizer = null
    }

    /**
     * Adapt a preset's band list to a different number of bands
     * by interpolating or truncating.
     */
    private fun adaptBands(bands: List<Int>, targetCount: Int): List<Int> {
        if (bands.isEmpty()) return List(targetCount) { 0 }
        if (targetCount <= bands.size) return bands.take(targetCount)
        // Linear interpolation to expand
        return List(targetCount) { i ->
            val ratio = i.toFloat() / (targetCount - 1) * (bands.size - 1)
            val low = ratio.toInt().coerceIn(0, bands.size - 2)
            val frac = ratio - low
            (bands[low] * (1 - frac) + bands[low + 1] * frac).toInt()
        }
    }
}

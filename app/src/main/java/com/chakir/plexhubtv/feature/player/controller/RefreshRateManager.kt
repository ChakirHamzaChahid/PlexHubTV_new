package com.chakir.plexhubtv.feature.player.controller

import android.app.Activity
import android.os.Build
import android.view.Display
import android.view.WindowManager
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Matches the display refresh rate to the video frame rate to eliminate judder.
 * For example, switches from 60Hz to 24Hz for 24fps film content.
 *
 * Only works on Android M+ (API 23) where Display.Mode APIs are available.
 * Automatically restores the original display mode on release.
 */
@Singleton
class RefreshRateManager @Inject constructor() {

    private var originalModeId: Int? = null

    fun matchRefreshRate(activity: Activity, videoFrameRate: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (videoFrameRate <= 0f) return

        val window = activity.window ?: return
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            window.windowManager.defaultDisplay
        } ?: return

        val currentMode = display.mode
        if (originalModeId == null) {
            originalModeId = currentMode.modeId
        }

        val supportedModes = display.supportedModes
        val bestMode = findBestMode(supportedModes, currentMode, videoFrameRate)

        if (bestMode != null && bestMode.modeId != currentMode.modeId) {
            val params = window.attributes
            params.preferredDisplayModeId = bestMode.modeId
            window.attributes = params
            Timber.i("RefreshRate: Switched from ${currentMode.refreshRate}Hz to ${bestMode.refreshRate}Hz for ${videoFrameRate}fps content")
        } else {
            Timber.d("RefreshRate: Current ${currentMode.refreshRate}Hz is already optimal for ${videoFrameRate}fps")
        }
    }

    fun restoreOriginalRate(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val savedModeId = originalModeId ?: return

        val window = activity.window ?: return
        val params = window.attributes
        params.preferredDisplayModeId = savedModeId
        window.attributes = params
        Timber.i("RefreshRate: Restored original display mode $savedModeId")
        originalModeId = null
    }

    /**
     * Find the best display mode that:
     * 1. Has the same resolution as the current mode
     * 2. Has a refresh rate that is an exact multiple of the video frame rate (eliminates judder)
     * 3. Prefers the lowest matching multiple (e.g. 24Hz over 48Hz for 24fps)
     */
    private fun findBestMode(
        modes: Array<Display.Mode>,
        currentMode: Display.Mode,
        videoFrameRate: Float,
    ): Display.Mode? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null

        return modes
            .filter {
                it.physicalWidth == currentMode.physicalWidth &&
                    it.physicalHeight == currentMode.physicalHeight
            }
            .filter { mode ->
                val ratio = mode.refreshRate / videoFrameRate
                val remainder = ratio % 1.0f
                // Allow exact multiples (24/24=1, 48/24=2, 60/30=2) with small tolerance
                remainder < 0.02f || remainder > 0.98f
            }
            .minByOrNull { it.refreshRate }
    }
}

package com.chakir.plexhubtv.feature.screensaver

import android.service.dreams.DreamService
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.designsystem.PlexHubTheme
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import timber.log.Timber

/**
 * DreamService (screensaver) for PlexHubTV.
 *
 * Uses manual Hilt EntryPoint instead of @AndroidEntryPoint because
 * DreamService has a non-standard lifecycle that causes silent injection
 * failures on some devices/Hilt versions.
 */
class PlexHubDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DreamServiceEntryPoint {
        fun mediaDao(): MediaDao
        fun settingsRepository(): SettingsRepository
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Timber.i("[Screensaver] onAttachedToWindow — setting up DreamService")

        try {
            isInteractive = false
            isFullscreen = true

            // Resolve dependencies from Hilt manually
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                DreamServiceEntryPoint::class.java,
            )
            val mediaDao = entryPoint.mediaDao()
            val settingsRepository = entryPoint.settingsRepository()

            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

            val viewModel = ScreensaverViewModel(mediaDao, settingsRepository)

            val composeView = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@PlexHubDreamService)
                setViewTreeViewModelStoreOwner(this@PlexHubDreamService)
                setViewTreeSavedStateRegistryOwner(this@PlexHubDreamService)
                setContent {
                    PlexHubTheme(appTheme = "OLEDBlack") {
                        ScreensaverContent(viewModel = viewModel)
                    }
                }
            }
            setContentView(composeView)

            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            Timber.i("[Screensaver] DreamService setup complete")
        } catch (e: Exception) {
            Timber.e(e, "[Screensaver] Failed to set up DreamService")
            finish()
        }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        Timber.i("[Screensaver] onDreamingStarted")
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        Timber.i("[Screensaver] onDreamingStopped")
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Timber.i("[Screensaver] onDetachedFromWindow — cleaning up")
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

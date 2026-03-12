package com.chakir.plexhubtv.feature.screensaver

import android.service.dreams.DreamService
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.chakir.plexhubtv.core.designsystem.PlexHubTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PlexHubDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true

        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@PlexHubDreamService)
            setViewTreeSavedStateRegistryOwner(this@PlexHubDreamService)
            setContent {
                PlexHubTheme(appTheme = "OLEDBlack") {
                    val viewModel: ScreensaverViewModel = viewModel()
                    ScreensaverContent(viewModel = viewModel)
                }
            }
        }
        setContentView(composeView)

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

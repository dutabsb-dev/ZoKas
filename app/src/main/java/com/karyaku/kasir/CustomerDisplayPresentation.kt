package com.karyaku.kasir

import android.app.Presentation
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.ViewTreeViewModelStoreOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner

class CustomerDisplayPresentation(
    private val activity: ComponentActivity,
    display: Display,
    private val viewModel: PosViewModel,
    private val settingsManager: SettingsManager
) : Presentation(activity, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val composeView = ComposeView(activity).apply {
            ViewTreeLifecycleOwner.set(this, activity)
            ViewTreeSavedStateRegistryOwner.set(this, activity)
            ViewTreeViewModelStoreOwner.set(this, activity)

            setContent {
                PosTheme(
                    isDarkMode = settingsManager.isDarkMode,
                    isHighContrast = settingsManager.isHighContrast,
                    language = settingsManager.language
                ) {
                    CfdContent(viewModel = viewModel, settingsManager = settingsManager, onDismiss = null)
                }
            }
        }
        
        setContentView(composeView)
    }
}
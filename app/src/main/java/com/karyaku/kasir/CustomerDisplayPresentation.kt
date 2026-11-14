package com.karyaku.kasir

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.compose.ui.platform.ViewTreeLifecycleOwner
import androidx.compose.ui.platform.ViewTreeSavedStateRegistryOwner
import androidx.compose.ui.platform.ViewTreeViewModelStoreOwner

class CustomerDisplayPresentation(
    private val context: Context,
    display: Display,
    private val viewModel: PosViewModel,
    private val settingsManager: SettingsManager
) : Presentation(context, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val composeView = ComposeView(context).apply {
            // Hubungkan Lifecycle dari Activity ke ComposeView di layar kedua
            (context as? LifecycleOwner)?.let { setViewTreeLifecycleOwner(it) }
            (context as? SavedStateRegistryOwner)?.let { setViewTreeSavedStateRegistryOwner(it) }
            (context as? ViewModelStoreOwner)?.let { setViewTreeViewModelStoreOwner(it) }

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
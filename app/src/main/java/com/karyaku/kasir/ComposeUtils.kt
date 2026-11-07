package com.karyaku.kasir

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import java.util.UUID

@Composable
fun <I, O> rememberSafeLauncher(
    contract: ActivityResultContract<I, O>,
    onResult: (O) -> Unit
): ActivityResultLauncher<I> {
    val context = LocalContext.current
    val activity = remember(context) {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is ComponentActivity) return@remember ctx
            ctx = ctx.baseContext
        }
        null
    } ?: error("Cannot find ComponentActivity in context hierarchy")

    val registry = activity.activityResultRegistry
    val currentOnResult = rememberUpdatedState(onResult)
    val key = remember { UUID.randomUUID().toString() }

    val launcher = remember(key) {
        registry.register(key, contract) { currentOnResult.value(it) }
    }

    DisposableEffect(key) {
        onDispose {
            launcher.unregister()
        }
    }
    return launcher
}
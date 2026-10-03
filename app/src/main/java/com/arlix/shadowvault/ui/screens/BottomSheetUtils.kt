package com.arlix.shadowvault.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun BottomSheetSecurity(
    showSheet: Boolean,
    onCloseSheet: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, showSheet) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && showSheet) {
                onCloseSheet()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

/**
 * [T4] Sheets and dialogs are separate windows, so the activity's "ignore touches when
 * another app's overlay covers us" setting does not apply to them. Call this inside every
 * ModalBottomSheet / Dialog / AlertDialog content.
 */
@Composable
fun ObscuredTouchGuard() {
    val view = LocalView.current
    SideEffect { view.rootView.filterTouchesWhenObscured = true }
}

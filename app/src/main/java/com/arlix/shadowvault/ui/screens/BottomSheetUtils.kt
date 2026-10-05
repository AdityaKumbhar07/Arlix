package com.arlix.shadowvault.ui.screens

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Closes the sheet as soon as the activity stops (the vault itself locks shortly after). */
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
 * Sheets and dialogs are separate windows, so the activity-level protections do not apply to
 * them. Call this inside every ModalBottomSheet / Dialog / AlertDialog content. It
 *  - drops touches while another app's overlay covers the window (tapjacking), and
 *  - keeps autofill services away from the fields in this window.
 */
@Composable
fun ObscuredTouchGuard() {
    val view = LocalView.current
    SideEffect {
        val root = view.rootView
        root.filterTouchesWhenObscured = true
        root.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }
}

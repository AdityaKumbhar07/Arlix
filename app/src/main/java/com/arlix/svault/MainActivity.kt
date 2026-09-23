package com.arlix.svault

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // [T1: Screen Capture & Background Snapshot Defense]
        // This makes the app show as a pure black square in the Recent Apps menu,
        // and blocks all screen recording/screenshots from the OS.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {
            MaterialTheme {
                // [T4: Tapjacking Defense]
                // filterTouchesWhenObscured drops all touches if an invisible overlay
                // is drawn on top of our app.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    content = {
                        Text(text = "Vault Engine Ready.")
                        // (We will hook up the actual UI here in the next steps!)
                    }
                )
            }
        }

        // Ensure the root view also rejects obscured touches
        window.decorView.rootView.filterTouchesWhenObscured = true
        // [T17: Trojan Autofill Ban]
        // Explicitly forbids the Android OS Autofill system from reading or interacting with our UI tree.
        window.decorView.importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }

    override fun onPause() {
        super.onPause()
        // [T7: Physical Snatch / T4: Overlay Pause Attack]
        // The instant the app loses perfect foreground focus (e.g., pulling down
        // the notification shade, screen turning off, or an overlay appearing),
        // we must command the LockVaultUseCase to instantly wipe the keys.

        // TODO: Call LockVaultUseCase() here when we wire up our Dependency Injection!
    }
}

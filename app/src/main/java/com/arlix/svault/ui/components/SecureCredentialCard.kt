package com.arlix.svault.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.arlix.svault.workers.ClipboardWipeWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.TimeUnit

@Composable
fun SecureCredentialCard(
    title: String,
    username: String,
    passwordSecret: CharArray
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isRevealed by remember { mutableStateOf(false) }

    // [T6 Bug fix] We track the active reveal job so we can cancel it before starting a new one.
    // Without this, tapping SHOW multiple times spawns multiple "hide after 5 seconds" coroutines
    // that all fire independently — the first one can hide the password early while the user
    // expects 5 more seconds of visibility from their most recent tap. Cancelling the previous
    // job before launching a new one makes the 5-second timer reset cleanly on every tap.
    var revealJob by remember { mutableStateOf<Job?>(null) }

    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(text = username, style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(8.dp))

            // [T6: Shoulder Surfing] — password is hidden by default
            val displayPassword = if (isRevealed) String(passwordSecret) else "••••••••••••••••"
            Text(text = displayPassword, style = MaterialTheme.typography.bodyLarge)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {

                // SHOW / HIDE button
                TextButton(onClick = {
                    isRevealed = true

                    // Cancel the previous timer before starting a fresh 5-second countdown.
                    // This is the fix for the race condition where multiple jobs competed to
                    // set isRevealed = false — the "SHOW" timer now always resets cleanly.
                    revealJob?.cancel()
                    revealJob = coroutineScope.launch {
                        delay(5000)
                        isRevealed = false
                        revealJob = null
                    }
                }) {
                    Text(if (isRevealed) "HIDING IN 5s" else "SHOW")
                }

                // COPY button
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

                    // Generate a stable token for this specific copy event.
                    // ClipboardWipeWorker will check this token before wiping — so it only wipes
                    // if the clipboard still holds *this* password, not something the user
                    // copied manually afterward (see ClipboardWipeWorker for the token check).
                    val clipToken = UUID.randomUUID().toString()

                    val clip = ClipData.newPlainText("password", String(passwordSecret))

                    // [T16: Keyboard Clipboard History] — Tell Android 13+ keyboards NOT to
                    // show this entry in their visual clipboard history tab
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        clip.description.extras = PersistableBundle().apply {
                            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                        }
                    }
                    clipboard.setPrimaryClip(clip)

                    // [T2: Clipboard Wipe] — Schedule a background wipe in 10 seconds.
                    // WorkManager is the correct tool here: it survives process death and OEM
                    // battery killers (within the WorkManager execution window).
                    val wipeRequest = OneTimeWorkRequestBuilder<ClipboardWipeWorker>()
                        .setInitialDelay(10, TimeUnit.SECONDS)
                        .setInputData(
                            androidx.work.workDataOf(ClipboardWipeWorker.KEY_CLIP_TOKEN to clipToken)
                        )
                        .build()
                    WorkManager.getInstance(context).enqueue(wipeRequest)
                }) {
                    Text("COPY")
                }
            }
        }
    }
}

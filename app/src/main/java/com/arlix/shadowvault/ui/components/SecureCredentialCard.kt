package com.arlix.shadowvault.ui.components

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
import com.arlix.shadowvault.workers.ClipboardWipeWorker
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

    // [T6] Tracks the active reveal job to reset the 5-second timer cleanly on multiple taps.
    var revealJob by remember { mutableStateOf<Job?>(null) }

    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(text = username, style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(8.dp))

            // [T6] Hide password by default.
            // [T11] Minimize string allocation to exact recomputations.
            val displayPassword = remember(isRevealed, passwordSecret) {
                if (isRevealed) String(passwordSecret) else "••••••••••••••••"
            }
            Text(text = displayPassword, style = MaterialTheme.typography.bodyLarge)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {

                TextButton(onClick = {
                    isRevealed = true
                    revealJob?.cancel()
                    revealJob = coroutineScope.launch {
                        delay(5000)
                        isRevealed = false
                        revealJob = null
                    }
                }) {
                    Text(if (isRevealed) "HIDING IN 5s" else "SHOW")
                }

                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

                    // Generate a stable token so ClipboardWipeWorker only wipes this specific copy event.
                    val clipToken = UUID.randomUUID().toString()

                    val clipboardText = String(passwordSecret)
                    val clip = ClipData.newPlainText("password", clipboardText)
                    
                    // [T16] Tell Android 13+ keyboards NOT to show this in visual clipboard history.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        clip.description.extras = PersistableBundle().apply {
                            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                        }
                    }
                    clipboard.setPrimaryClip(clip)

                    // [T2] WorkManager schedules a reliable background wipe in 10 seconds.
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

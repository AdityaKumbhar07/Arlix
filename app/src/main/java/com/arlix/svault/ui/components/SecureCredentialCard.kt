package com.arlix.svault.ui.components

import android.content.ClipData
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SecureCredentialCard(
    title: String,
    username: String,
    passwordSecret: CharArray
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State to track if the password is currently revealed
    var isRevealed by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(text = username, style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(8.dp))

            // [T6: Shoulder Surfing]
            // Render actual chars if revealed, otherwise render dots
            val displayPassword = if (isRevealed) String(passwordSecret) else "••••••••••••••••"
            Text(text = displayPassword, style = MaterialTheme.typography.bodyLarge)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {

                // SHOW BUTTON LOGIC
                TextButton(onClick = {
                    isRevealed = true
                    // Auto-hide after 5 seconds
                    coroutineScope.launch {
                        delay(5000)
                        isRevealed = false
                    }
                }) {
                    Text(if (isRevealed) "HIDING IN 5s" else "SHOW")
                }

                // COPY BUTTON LOGIC
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("password", String(passwordSecret))

                    // [T16: Keyboard Clipboard Evasion]
                    // Tell Android 13+ keyboards NOT to show this in their UI history
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        clip.description.extras = PersistableBundle().apply {
                            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                        }
                    }
                    clipboard.setPrimaryClip(clip)
                }) {
                    Text("COPY")
                }
            }
        }
    }
}

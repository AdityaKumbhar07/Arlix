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
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun SecureCredentialCard(
    title: String,
    username: String,
    passwordSecret: CharArray
) {
    val context = LocalContext.current
    var isRevealed by remember { mutableStateOf(false) }

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
            // [T3] Hides password from accessibility scrapers when revealed.
            Text(
                text = displayPassword,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics { hideFromAccessibility() }
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {

                TextButton(onClick = {
                    isRevealed = !isRevealed
                }) {
                    Text(if (isRevealed) "HIDE" else "SHOW")
                }

                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipboardText = String(passwordSecret)
                    val clip = ClipData.newPlainText("Password", clipboardText)

                    // [T16] Tell Android 13+ keyboards NOT to show this in visual clipboard history preview.
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

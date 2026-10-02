package com.arlix.shadowvault.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arlix.shadowvault.ui.theme.TextMuted
import com.arlix.shadowvault.ui.theme.TextPrimary

@Composable
fun SecureCredentialCard(
    title: String,
    username: String,
    passwordSecret: CharArray,
    isColdVault: Boolean = false
) {
    val context = LocalContext.current
    var isRevealed by remember { mutableStateOf(false) }

    val backgroundColor = if (isColdVault) com.arlix.shadowvault.ui.theme.ColdVaultCanvas else com.arlix.shadowvault.ui.theme.HotVaultCanvas
    val borderColor = if (isColdVault) com.arlix.shadowvault.ui.theme.ColdVaultBorder else com.arlix.shadowvault.ui.theme.HotVaultBorder

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Title, Username, and Actions (Edit/Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }

                Row(horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { /* Dummy Edit */ }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = { /* Dummy Delete */ }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Row: Password and Actions (Eye/Copy)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(borderColor.copy(alpha = 0.3f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // [T6] Hide password by default.
                // [T11] Minimize string allocation to exact recomputations.
                val displayPassword = remember(isRevealed, passwordSecret) {
                    if (isRevealed) String(passwordSecret) else "••••••••••••••••"
                }

                // [T3] Hides password from accessibility scrapers when revealed.
                Text(
                    text = displayPassword,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    modifier = Modifier
                        .semantics { hideFromAccessibility() }
                        .weight(1f)
                )

                Row(horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = {
                        isRevealed = !isRevealed
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (isRevealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (isRevealed) "Hide" else "Show",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(onClick = {
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
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(com.arlix.shadowvault.R.drawable.ic_copy),
                            contentDescription = "Copy",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

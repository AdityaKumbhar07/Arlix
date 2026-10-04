package com.arlix.shadowvault.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arlix.shadowvault.R
import com.arlix.shadowvault.ui.screens.ObscuredTouchGuard
import com.arlix.shadowvault.ui.theme.ColdVaultAccent
import com.arlix.shadowvault.ui.theme.ColdVaultBorder
import com.arlix.shadowvault.ui.theme.ColdVaultCanvas
import com.arlix.shadowvault.ui.theme.HotVaultAccent
import com.arlix.shadowvault.ui.theme.HotVaultBorder
import com.arlix.shadowvault.ui.theme.HotVaultCanvas
import com.arlix.shadowvault.ui.theme.PlusJakartaSansFontFamily
import com.arlix.shadowvault.ui.theme.TextMuted
import com.arlix.shadowvault.ui.theme.TextPrimary

@Composable
fun CredentialListItem(
    title: String,
    username: String,
    isColdVault: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isColdVault) ColdVaultBorder else HotVaultBorder
    val initial = title.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 7.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(borderColor, RoundedCornerShape(17.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Thin,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.width(17.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                Text(
                    text = username,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }

            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun CredentialDetailSheetContent(
    title: String,
    username: String,
    passwordSecret: CharArray,
    notes: String,
    category: String,
    isColdVault: Boolean,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var isRevealed by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val canvasColor = if (isColdVault) ColdVaultCanvas else HotVaultCanvas
    val borderColor = if (isColdVault) ColdVaultBorder else HotVaultBorder
    val accentColor = if (isColdVault) ColdVaultAccent else HotVaultAccent
    val initial = title.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val deleteRed = Color(0xFFD95757)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(canvasColor)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 0.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(borderColor, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .border(1.dp, borderColor, RoundedCornerShape(15.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, borderColor, CircleShape)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "Close",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(15.dp))
        HorizontalDivider(color = borderColor, thickness = 1.dp)
        Spacer(modifier = Modifier.height(20.dp))

        // Username Card
        DetailCardBox(title = "USERNAME / ACCOUNT", borderColor = borderColor) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = username,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
                IconButton(
                    onClick = { copyToClipboard(context, username) },
                    modifier = Modifier
                        .size(40.dp)
                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_copy),
                        contentDescription = "Copy Username",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(15.dp))

        // Password Card
        DetailCardBox(title = "PASSWORD / SECRET", borderColor = borderColor) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayPassword = remember(isRevealed, passwordSecret) {
                    if (isRevealed) String(passwordSecret) else "••••••••••••••••"
                }

                Text(
                    text = displayPassword,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        letterSpacing = if (isRevealed) 0.sp else 2.sp
                    ),
                    color = TextPrimary,
                    modifier = Modifier
                        .semantics { hideFromAccessibility() }
                        .weight(1f)
                )

                Row(horizontalArrangement = Arrangement.End) {
                    IconButton(
                        onClick = { isRevealed = !isRevealed },
                        modifier = Modifier
                            .size(40.dp)
                            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            painter = painterResource(if (isRevealed) R.drawable.ic_eye_slash else R.drawable.ic_eye),
                            contentDescription = "Reveal",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = { copyToClipboard(context, String(passwordSecret), true) },
                        modifier = Modifier
                            .size(40.dp)
                            .background(accentColor, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_copy),
                            contentDescription = "Copy Password",
                            tint = canvasColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(15.dp))

        // Notes Card
        DetailCardBox(title = "NOTES & DETAILS", borderColor = borderColor) {
            val hasNotes = notes.isNotBlank()
            Text(
                text = if (hasNotes) notes else "No notes available for this credential.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (hasNotes) TextPrimary else TextMuted
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(15.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = "Edit",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Edit Credential",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                }
            }

            Button(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFF0F0))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        tint = deleteRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.labelLarge,
                        color = deleteRed
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = canvasColor,
            title = { Text("Delete credential?", color = TextPrimary) },
            text = {
                ObscuredTouchGuard()
                Text(
                    "\"$title\" will be permanently deleted. This cannot be undone.",
                    color = TextMuted
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = deleteRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextPrimary)
                }
            }
        )
    }
}

@Composable
fun DetailCardBox(title: String, borderColor: Color, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

private fun copyToClipboard(context: Context, text: String, isSensitive: Boolean = false) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("VaultData", text)
    if (isSensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
}

package com.arlix.shadowvault.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arlix.shadowvault.R
import com.arlix.shadowvault.ui.SecurePassphraseField
import com.arlix.shadowvault.ui.theme.ColdVaultAccent
import com.arlix.shadowvault.ui.theme.ColdVaultBorder
import com.arlix.shadowvault.ui.theme.ColdVaultCanvas
import com.arlix.shadowvault.ui.theme.HotVaultAccent
import com.arlix.shadowvault.ui.theme.HotVaultBorder
import com.arlix.shadowvault.ui.theme.HotVaultCanvas
import com.arlix.shadowvault.ui.theme.TextMuted
import com.arlix.shadowvault.ui.theme.TextPrimary
import com.arlix.shadowvault.ui.toSecretChars

private enum class ToolMode { MENU, CHANGE, BACKUP }

/**
 * Tools for the open vault: change passphrase, back up, restore. Shared by the hot and cold
 * screens; only the colors differ. All passphrase fields use the secure field and are
 * cleared after every attempt, like the unlock screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceSheet(
    isColdVault: Boolean,
    working: Boolean,
    onDismiss: () -> Unit,
    onChangePassphrase: (current: CharArray, new: CharArray, confirm: CharArray) -> Unit,
    onCreateBackup: (passphrase: CharArray, confirm: CharArray) -> Unit,
    onRestore: () -> Unit
) {
    val canvas = if (isColdVault) ColdVaultCanvas else HotVaultCanvas
    val border = if (isColdVault) ColdVaultBorder else HotVaultBorder
    val accent = if (isColdVault) ColdVaultAccent else HotVaultAccent

    // remember, NOT rememberSaveable: passphrase text must never be saved.
    var mode by remember { mutableStateOf(ToolMode.MENU) }
    val first = remember { TextFieldState() }
    val second = remember { TextFieldState() }
    val third = remember { TextFieldState() }

    fun clearFields() {
        first.clearText()
        second.clearText()
        third.clearText()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = canvas,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        ObscuredTouchGuard()
        BottomSheetSecurity(showSheet = true, onCloseSheet = onDismiss)

        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 15.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isColdVault) "Cold vault tools" else "Hot vault tools",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp).border(1.dp, border, CircleShape)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Close",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = border, thickness = 1.dp)
            Spacer(modifier = Modifier.height(20.dp))

            if (working) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = accent)
                }
                return@Column
            }

            when (mode) {
                ToolMode.MENU -> {
                    Text(
                        "Your data stays encrypted. Arlix itself never uses the network; you choose where a backup file goes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    ToolButton("Change passphrase", false, accent, canvas, border) {
                        clearFields(); mode = ToolMode.CHANGE
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    ToolButton("Back up (e.g. to Google Drive)", false, accent, canvas, border) {
                        clearFields(); mode = ToolMode.BACKUP
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    ToolButton("Restore from backup", false, accent, canvas, border) { onRestore() }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Restore merges into THIS vault: new entries are added, newer versions win, nothing is deleted. The app locks while the file picker is open; unlock again to finish.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }

                ToolMode.CHANGE -> {
                    Text(
                        "Enter your current passphrase, then the new one. Afterwards the vault locks and you unlock with the new passphrase.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    SecurePassphraseField(first, "Current passphrase", Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                    SecurePassphraseField(second, "New passphrase", Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                    SecurePassphraseField(third, "Confirm new passphrase", Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(20.dp))
                    ToolButton(
                        "Change passphrase", true, accent, canvas, border,
                        enabled = first.text.isNotEmpty() && second.text.isNotEmpty() && third.text.isNotEmpty()
                    ) {
                        onChangePassphrase(first.toSecretChars(), second.toSecretChars(), third.toSecretChars())
                        clearFields()
                    }
                    TextButton(onClick = { clearFields(); mode = ToolMode.MENU }) {
                        Text("Back", color = TextPrimary)
                    }
                }

                ToolMode.BACKUP -> {
                    Text(
                        "Choose a passphrase for the backup file (it can be the same as your vault passphrase, longer is better). Then Android's file picker opens: pick Google Drive (needs the Drive app) or any other location. You need this passphrase to restore.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    SecurePassphraseField(first, "Backup passphrase", Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                    SecurePassphraseField(second, "Confirm backup passphrase", Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(20.dp))
                    ToolButton(
                        "Create encrypted backup", true, accent, canvas, border,
                        enabled = first.text.isNotEmpty() && second.text.isNotEmpty()
                    ) {
                        onCreateBackup(first.toSecretChars(), second.toSecretChars())
                        clearFields()
                    }
                    TextButton(onClick = { clearFields(); mode = ToolMode.MENU }) {
                        Text("Back", color = TextPrimary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ToolButton(
    text: String,
    filled: Boolean,
    accent: Color,
    canvas: Color,
    border: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val modifier = Modifier.fillMaxWidth().height(56.dp)
    val shape = RoundedCornerShape(12.dp)
    if (filled) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            shape = shape,
            colors = ButtonDefaults.buttonColors(containerColor = accent)
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = canvas)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            shape = shape,
            border = BorderStroke(1.dp, border)
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
        }
    }
}

/** Asks for the backup passphrase of a picked backup file. Shown only in the vault it was started from. */
/** Asks for the backup passphrase of a picked backup file. Shown only in the vault it was started from. */
@Composable
fun RestorePassphraseDialog(
    isColdVault: Boolean,
    working: Boolean,
    onConfirm: (CharArray) -> Unit,
    onCancel: () -> Unit
) {
    val state = remember { TextFieldState() }
    val accent = if (isColdVault) ColdVaultAccent else HotVaultAccent
    AlertDialog(
        onDismissRequest = { if (!working) onCancel() },
        containerColor = if (isColdVault) ColdVaultCanvas else HotVaultCanvas,
        title = {
            Text(
                if (isColdVault) "Restore into cold vault" else "Restore into hot vault",
                color = TextPrimary
            )
        },
        text = {
            ObscuredTouchGuard()
            Column {
                Text(
                    "Enter the backup passphrase. Entries are merged into this vault; newer versions win and nothing is deleted.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                SecurePassphraseField(state, "Backup passphrase", Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            if (working) {
                CircularProgressIndicator(color = accent, modifier = Modifier.size(24.dp))
            } else {
                TextButton(
                    enabled = state.text.isNotEmpty(),
                    onClick = {
                        onConfirm(state.toSecretChars())
                        state.clearText()
                    }
                ) { Text("Restore", color = TextPrimary) }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !working) { Text("Cancel", color = TextPrimary) }
        }
    )
}

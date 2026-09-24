package com.arlix.svault.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arlix.svault.domain.VaultEntry
import com.arlix.svault.ui.components.SecureCredentialCard
import com.arlix.svault.ui.SecureVaultTextField

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    entries: List<VaultEntry>,
    isColdVault: Boolean,
    coldVaultExists: Boolean,           // true = "Enter key" mode; false = "Create key" mode
    coldVaultError: String?,            // non-null = show inline error in the dialog (Bug 1a fix)
    onLock: () -> Unit,
    onColdVaultUnlock: (CharArray) -> Unit,
    onColdVaultCreate: (password: CharArray, confirm: CharArray) -> Unit,
    onDismissColdVaultError: () -> Unit, // tells ViewModel to clear _coldVaultError
    onAddClicked: () -> Unit
) {
    var showColdVaultDialog by remember { mutableStateOf(false) }

    // When the ViewModel reports a cold vault error (from an async operation), make sure
    // the dialog is still visible so the user can read the message and fix their input.
    // Without this, the dialog would close before the error arrives from the coroutine.
    LaunchedEffect(coldVaultError) {
        if (coldVaultError != null) {
            showColdVaultDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        // We never announce "Cold Vault" in the UI — an adversary watching
                        // the screen should see nothing different from the Hot Vault.
                        text = "ShadowVault",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // THE STEALTH COLD VAULT TRIGGER
                    // Short tap is a no-op decoy. Only a long-press triggers the dialog.
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "App Info",
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .combinedClickable(
                                onClick = { /* Deliberate no-op: short tap is the decoy */ },
                                onLongClick = {
                                    if (!isColdVault) {
                                        showColdVaultDialog = true
                                    }
                                }
                            )
                    )
                    IconButton(onClick = onLock) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock Vault")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClicked) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Credential")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(entries) { entry ->
                SecureCredentialCard(
                    title = entry.title,
                    username = entry.username,
                    passwordSecret = entry.passwordSecret
                )
            }
        }
    }

    // THE COLD VAULT DIALOG
    //
    // MODE LOGIC:
    //   coldVaultExists = false → First-time setup: two fields + "CREATE" button
    //   coldVaultExists = true  → Normal entry: one field + "VERIFY" button
    //
    // ERROR DISPLAY (Bug 1a fix):
    //   Validation errors (wrong length, mismatch) come from ViewModel via [coldVaultError].
    //   They are shown inline in the dialog WITHOUT closing it or navigating away.
    //   The dialog STAYS OPEN until the user fixes their input or cancels.
    //
    // SUBMIT BEHAVIOUR (Bug 1b/1c fix):
    //   We do NOT close the dialog on submit. The dialog stays open while the coroutine runs.
    //   On success, the ViewModel transitions to Unlocked → the whole screen replaces naturally.
    //   On failure, [coldVaultError] gets a message and the dialog remains open.
    if (showColdVaultDialog) {
        var coldPassword by remember { mutableStateOf("") }
        var coldConfirm by remember { mutableStateOf("") }

        val isFirstTimeSetup = !coldVaultExists

        AlertDialog(
            onDismissRequest = {
                showColdVaultDialog = false
                coldPassword = ""
                coldConfirm = ""
                onDismissColdVaultError()
            },
            title = {
                Text("Advanced Security") // UI-level discretion only — prevents casual/shoulder-surfing discovery. Does NOT provide forensic-grade deniability; the existence of vault_secondary.db is discoverable by anyone with filesystem access. Explicitly out of scope.
            },
            text = {
                Column {
                    SecureVaultTextField(
                        value = coldPassword,
                        onValueChange = { coldPassword = it },
                        label = if (isFirstTimeSetup) "Set Cold Vault Passphrase" else "Cold Vault Key"
                    )
                    if (isFirstTimeSetup) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SecureVaultTextField(
                            value = coldConfirm,
                            onValueChange = { coldConfirm = it },
                            label = "Confirm Passphrase"
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "This passphrase is separate from your Hot Vault passphrase.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Inline error display — visible without closing the dialog (Bug 1a fix)
                    if (coldVaultError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = coldVaultError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // [Bug 1b/1c fix] Do NOT close the dialog on submit.
                        // Keep it open while the async operation runs. If it succeeds,
                        // the UI naturally transitions to the Cold Vault Dashboard.
                        // If it fails, coldVaultError will be set and shown above.
                        onDismissColdVaultError() // clear previous error before new attempt
                        if (isFirstTimeSetup) {
                            onColdVaultCreate(coldPassword.toCharArray(), coldConfirm.toCharArray())
                        } else {
                            onColdVaultUnlock(coldPassword.toCharArray())
                        }
                        // Don't clear the text fields yet — let the user see what they typed
                        // if an error comes back (e.g. "Passphrase too short")
                    },
                    enabled = coldPassword.isNotEmpty() && (!isFirstTimeSetup || coldConfirm.isNotEmpty())
                ) {
                    Text(if (isFirstTimeSetup) "CREATE" else "VERIFY")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showColdVaultDialog = false
                    coldPassword = ""
                    coldConfirm = ""
                    onDismissColdVaultError()
                }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

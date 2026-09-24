package com.arlix.svault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arlix.svault.ui.VaultUiState
import com.arlix.svault.ui.SecureVaultTextField

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock

/**
 * Covers two distinct modes in one composable:
 *
 * 1. SETUP MODE (uiState is VaultUiState.Setup):
 *    First-ever launch. The DB file doesn't exist yet.
 *    Shows "Create Master Passphrase" + a confirmation field.
 *    Calls [onCreateVault] on submit.
 *
 * 2. LOCKED MODE (uiState is Locked / Unlocking / Error):
 *    Normal unlock. The DB file exists.
 *    Shows "Enter Master Passphrase".
 *    Calls [onUnlock] on submit.
 *
 * Keeping both modes in one composable avoids a Crossfade flicker between two nearly-identical
 * screens, and the visual difference (one field vs two fields + heading copy) is minimal.
 */
@Composable
fun LockScreen(
    uiState: VaultUiState,
    onUnlock: (CharArray) -> Unit,
    onCreateVault: (password: CharArray, confirm: CharArray) -> Unit
) {
    val isSetupMode = uiState is VaultUiState.Setup

    // Compose-side Strings for rendering only. Converted to CharArray before being sent down.
    var passwordInput by remember { mutableStateOf("") }
    var confirmInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Vault Locked",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "ShadowVault",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Mode-dependent subtitle
        Text(
            text = if (isSetupMode) "Create your Master Passphrase" else "Enter your Master Passphrase",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Primary passphrase field
        SecureVaultTextField(
            value = passwordInput,
            onValueChange = { passwordInput = it },
            label = if (isSetupMode) "New Master Passphrase" else "Master Passphrase",
            modifier = Modifier.fillMaxWidth()
        )

        // Confirm field — only visible in Setup mode
        if (isSetupMode) {
            Spacer(modifier = Modifier.height(12.dp))
            SecureVaultTextField(
                value = confirmInput,
                onValueChange = { confirmInput = it },
                label = "Confirm Passphrase",
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Error / status display
        if (uiState is VaultUiState.Error) {
            Text(
                text = uiState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Button or spinner
        if (uiState is VaultUiState.Unlocking) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    if (isSetupMode) {
                        // createVault path — send both fields as CharArrays, clear UI immediately
                        onCreateVault(passwordInput.toCharArray(), confirmInput.toCharArray())
                        passwordInput = ""
                        confirmInput = ""
                    } else {
                        // Normal unlock path
                        onUnlock(passwordInput.toCharArray())
                        passwordInput = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = passwordInput.isNotEmpty() && (!isSetupMode || confirmInput.isNotEmpty())
            ) {
                Text(if (isSetupMode) "CREATE VAULT" else "UNLOCK")
            }
        }

        if (isSetupMode) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Minimum 5 characters. This passphrase cannot be recovered if forgotten.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

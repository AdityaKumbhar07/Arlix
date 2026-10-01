package com.arlix.shadowvault.ui.screens

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arlix.shadowvault.R
import com.arlix.shadowvault.ui.SecureVaultTextField
import com.arlix.shadowvault.ui.VaultUiState
import com.arlix.shadowvault.ui.theme.*

@Composable
fun rememberIsThirdPartyImeActive(): Boolean {
    val context = LocalContext.current
    return remember {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val currentImeId = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val currentIme = imm.enabledInputMethodList.find { it.id == currentImeId }
        val packageName = currentIme?.packageName
        val isSystemApp = packageName?.let {
            try {
                val appInfo = context.packageManager.getApplicationInfo(it, 0)
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        } ?: false
        !isSystemApp
    }
}

@Composable
fun LockScreen(
    uiState: VaultUiState,
    onUnlock: (CharArray) -> Unit,
    onCreateVault: (password: CharArray, confirm: CharArray) -> Unit
) {
    val isSetupMode = uiState is VaultUiState.Setup || (uiState is VaultUiState.Error && uiState.isSetupMode)

    if (isSetupMode) {
        SetupScreen(uiState, onCreateVault)
    } else {
        UnlockScreen(uiState, onUnlock)
    }
}

@Composable
fun SetupScreen(
    uiState: VaultUiState,
    onCreateVault: (password: CharArray, confirm: CharArray) -> Unit
) {
    var passwordInput by remember { mutableStateOf("") }
    var confirmInput by remember { mutableStateOf("") }
    val isThirdPartyIme = rememberIsThirdPartyImeActive()

    Box(modifier = Modifier.fillMaxSize().background(HotVaultCanvas)) {
        // Status badge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(HotVaultBorder, CircleShape)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "FIRST-TIME SETUP",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                color = TextMuted
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(HotVaultBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shield),
                    contentDescription = "Shield",
                    modifier = Modifier.size(32.dp),
                    tint = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Create Master Key",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Minimum 5 characters. This passphrase cannot be recovered if forgotten.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(32.dp))

            if (isThirdPartyIme) {
                Text(
                    text = "⚠ A third-party keyboard is active. It may log keystrokes.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            SecureVaultTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                label = "New Master Passphrase",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            SecureVaultTextField(
                value = confirmInput,
                onValueChange = { confirmInput = it },
                label = "Confirm Passphrase",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (uiState is VaultUiState.Error) {
                Text(
                    text = uiState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (uiState is VaultUiState.Unlocking) {
                CircularProgressIndicator(color = HotVaultAccent)
            } else {
                Button(
                    onClick = {
                        onCreateVault(passwordInput.toCharArray(), confirmInput.toCharArray())
                        passwordInput = ""
                        confirmInput = ""
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = passwordInput.isNotEmpty() && confirmInput.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = HotVaultAccent)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Create master key",
                            style = MaterialTheme.typography.labelLarge,
                            color = HotVaultCanvas
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = HotVaultCanvas,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Footer
        Text(
            text = "Protected by Dual-Chamber Architecture",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
        )
    }
}

@Composable
fun UnlockScreen(
    uiState: VaultUiState,
    onUnlock: (CharArray) -> Unit
) {
    var passwordInput by remember { mutableStateOf("") }
    val isThirdPartyIme = rememberIsThirdPartyImeActive()

    Box(modifier = Modifier.fillMaxSize().background(HotVaultCanvas)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(HotVaultBorder, CircleShape)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "DAILY UNLOCK",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                color = TextMuted
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(HotVaultBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lock),
                    contentDescription = "Lock",
                    modifier = Modifier.size(32.dp),
                    tint = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Arlix",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enter your Master Passphrase",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(32.dp))

            if (isThirdPartyIme) {
                Text(
                    text = "⚠ A third-party keyboard is active. It may log keystrokes.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            SecureVaultTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                label = "Master Passphrase",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (uiState is VaultUiState.Error) {
                Text(
                    text = uiState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (uiState is VaultUiState.Unlocking) {
                CircularProgressIndicator(color = HotVaultAccent)
            } else {
                Button(
                    onClick = {
                        onUnlock(passwordInput.toCharArray())
                        passwordInput = ""
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = passwordInput.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = HotVaultAccent)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Unlock vault",
                            style = MaterialTheme.typography.labelLarge,
                            color = HotVaultCanvas
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = HotVaultCanvas,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Text(
            text = "Protected by Dual-Chamber Architecture",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
        )
    }
}

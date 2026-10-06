package com.arlix.shadowvault.ui.screens

import android.content.Context
import android.content.pm.ApplicationInfo
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.arlix.shadowvault.R
import com.arlix.shadowvault.ui.SecurePassphraseField
import com.arlix.shadowvault.ui.VaultUiState
import com.arlix.shadowvault.ui.theme.HotVaultAccent
import com.arlix.shadowvault.ui.theme.HotVaultBorder
import com.arlix.shadowvault.ui.theme.HotVaultCanvas
import com.arlix.shadowvault.ui.theme.TextMuted
import com.arlix.shadowvault.ui.theme.TextPrimary
import com.arlix.shadowvault.ui.toSecretChars

/** True if the default keyboard is not a system app. Unknown counts as "warn". */
private fun isThirdPartyImeActive(context: Context): Boolean = runCatching {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val currentId = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        ?: return@runCatching true
    val ime = imm.enabledInputMethodList.firstOrNull { it.id == currentId }
        ?: return@runCatching true
    // Read the flag straight from the keyboard's own service info (no package lookup needed).
    (ime.serviceInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
}.getOrDefault(true)

@Composable
fun rememberIsThirdPartyImeActive(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var active by remember { mutableStateOf(isThirdPartyImeActive(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) active = isThirdPartyImeActive(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return active
}

@Composable
fun LockScreen(
    uiState: VaultUiState,
    onUnlock: (CharArray) -> Unit,
    onCreateVault: (password: CharArray, confirm: CharArray) -> Unit
) {
    val isSetupMode = uiState is VaultUiState.Setup || (uiState is VaultUiState.Error && uiState.isSetupMode)
    if (isSetupMode) SetupScreen(uiState, onCreateVault) else UnlockScreen(uiState, onUnlock)
}

@Composable
fun SetupScreen(
    uiState: VaultUiState,
    onCreateVault: (password: CharArray, confirm: CharArray) -> Unit
) {
    // remember, NOT rememberSaveable / rememberTextFieldState: the text must never be saved.
    val passwordState = remember { TextFieldState() }
    val confirmState = remember { TextFieldState() }

    LockScreenLayout(
        badge = "FIRST-TIME SETUP",
        iconDescription = "Shield",
        title = "Create Master Key",
        subtitle = "Minimum 5 characters. This passphrase cannot be recovered if forgotten.",
        uiState = uiState,
        buttonText = "Create master key",
        buttonEnabled = passwordState.text.isNotEmpty() && confirmState.text.isNotEmpty(),
        onButtonClick = {
            onCreateVault(passwordState.toSecretChars(), confirmState.toSecretChars())
            passwordState.clearText()
            confirmState.clearText()
        }
    ) {
        SecurePassphraseField(passwordState, "New Master Passphrase", Modifier.fillMaxWidth().testTag("SetupPasswordField"))
        Spacer(modifier = Modifier.height(12.dp))
        SecurePassphraseField(confirmState, "Confirm Passphrase", Modifier.fillMaxWidth().testTag("SetupConfirmPasswordField"))
    }
}

@Composable
fun UnlockScreen(
    uiState: VaultUiState,
    onUnlock: (CharArray) -> Unit
) {
    val passwordState = remember { TextFieldState() }

    LockScreenLayout(
        badge = "DAILY UNLOCK",
        iconDescription = "Lock",
        title = "Arlix",
        subtitle = "Enter your Master Passphrase",
        uiState = uiState,
        buttonText = "Unlock vault",
        buttonEnabled = passwordState.text.isNotEmpty(),
        onButtonClick = {
            onUnlock(passwordState.toSecretChars())
            passwordState.clearText()
        }
    ) {
        SecurePassphraseField(passwordState, "Master Passphrase", Modifier.fillMaxWidth().testTag("LoginPasswordField"))
    }
}

/** Shared layout for the setup and unlock screens (they were near-identical copies). */
@Composable
private fun LockScreenLayout(
    badge: String,
    iconDescription: String,
    title: String,
    subtitle: String,
    uiState: VaultUiState,
    buttonText: String,
    buttonEnabled: Boolean,
    onButtonClick: () -> Unit,
    fields: @Composable ColumnScope.() -> Unit
) {
    val isThirdPartyIme = rememberIsThirdPartyImeActive()

    Box(modifier = Modifier.fillMaxSize().background(HotVaultCanvas)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color.White, CircleShape)
                .border(1.dp, HotVaultBorder, CircleShape)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                color = TextMuted
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.White, RoundedCornerShape(17.dp))
                    .border(1.dp, HotVaultBorder, RoundedCornerShape(17.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shield),
                    contentDescription = iconDescription,
                    modifier = Modifier.size(32.dp),
                    tint = Color.Unspecified
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            Spacer(modifier = Modifier.height(32.dp))

            if (isThirdPartyIme) {
                Text(
                    text = "⚠ A third-party keyboard is active. It may log keystrokes.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            fields()
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
                    onClick = onButtonClick,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = buttonEnabled,
                    colors = ButtonDefaults.buttonColors(containerColor = HotVaultAccent)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = buttonText,
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
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
        )
    }
}

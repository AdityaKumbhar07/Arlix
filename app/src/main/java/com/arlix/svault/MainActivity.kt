package com.arlix.svault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.arlix.svault.ui.theme.ArlixTheme


// Declares mutually exclusive presentation states of the vault.

enum class E_ui_VaultState {
    LOCKED, UNLOCKED
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArlixTheme {
                var v_ui_vaultState by remember { mutableStateOf(E_ui_VaultState.LOCKED)}

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                // Unidirectional state-driven screen switch
                    when(v_ui_vaultState) {
                        E_ui_VaultState.LOCKED -> C_ui_LockScreen(
                            modifier = Modifier.padding(innerPadding),
                                onUnlockSuccess = { v_ui_vaultState = E_ui_VaultState.UNLOCKED }
                        )

                        E_ui_VaultState.UNLOCKED -> C_ui_DashboardScreen(
                            modifier = Modifier.padding(innerPadding),
                                onLockClicked = { v_ui_vaultState = E_ui_VaultState.LOCKED }
                        )
                    }
                }
            }
        }
    }
}

// Vault Lock screen

@Composable
fun C_ui_LockScreen(modifier: Modifier = Modifier, onUnlockSuccess: () -> Unit) {

    var v_ui_passwordInput by remember { mutableStateOf("")}
    var v_ui_isPasswordVisible by remember { mutableStateOf(false)}
    var v_ui_statusMessage by remember { mutableStateOf("")}

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🛡️ ShadowVault",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        //Password field
        OutlinedTextField(
            value = v_ui_passwordInput,
            onValueChange = { v_ui_passwordInput = it},
            label = { Text("Master Password")},
            singleLine = true,
            visualTransformation = if (v_ui_isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(onClick = {v_ui_isPasswordVisible = !v_ui_isPasswordVisible}) {
                    Text(if(v_ui_isPasswordVisible) "HIDE" else "SHOW")
                }
            }
        )
        //Checks if box is empty
        if (v_ui_statusMessage.isNotEmpty()) {
            Text(
                text = v_ui_statusMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
                onClick = {
                    if(v_ui_passwordInput.isBlank()) {
                        v_ui_statusMessage = "Passphrase can't be empty"
                    } else {
                        v_ui_statusMessage = ""
                        onUnlockSuccess()
                    }
                },
                modifier = Modifier.fillMaxWidth()
        ) {
            Text("Unlock Vault")
        }
    }
}

// Dashboard

@Composable
fun C_ui_DashboardScreen(modifier: Modifier = Modifier, onLockClicked: () -> Unit ){
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Unlocked Vault", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onLockClicked,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("LOCK VAULT")
        }
    }
}

//Dev env preview
//@Preview(showBackground = true)
//@Composable
//fun LockScreenPreview() {
//    ArlixTheme {
//        C_ui_LockScreen(onUnlockSuccess = {})
//    }
//}
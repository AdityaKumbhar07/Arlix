package com.arlix.svault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.arlix.svault.ui.C_ui_CredentialItem
import com.arlix.svault.ui.C_ui_VaultViewModel
import com.arlix.svault.ui.E_ui_HotSection
import com.arlix.svault.ui.E_ui_VaultChamber
import com.arlix.svault.ui.theme.ArlixTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Declares mutually exclusive presentation states of the vault.

enum class E_ui_VaultState {
    LOCKED, UNLOCKED
}

class MainActivity : ComponentActivity() {
    private val v_ui_viewModel: C_ui_VaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Security flags(deniel access)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()
        setContent {
            ArlixTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                // Unidirectional state-driven screen switch
                    when(v_ui_viewModel.v_ui_vaultState) {
                        E_ui_VaultState.LOCKED -> C_ui_LockScreen(
                            modifier = Modifier.padding(innerPadding),
                            v_ui_viewModel = v_ui_viewModel
                        )

                        E_ui_VaultState.UNLOCKED -> C_ui_DashboardScreen(
                            modifier = Modifier.padding(innerPadding),
                            v_ui_viewModel = v_ui_viewModel
                        )
                    }
                }
            }
        }
    }
    override fun onStop() {
        super.onStop()
        if(!isChangingConfigurations) {
            v_ui_viewModel.f_ui_lockImmediate()
        }
    }

}

// Vault Lock screen

@Composable
fun C_ui_LockScreen(modifier: Modifier = Modifier, v_ui_viewModel: C_ui_VaultViewModel) {

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
            value = v_ui_viewModel.v_ui_passwordInput,
            onValueChange = { v_ui_viewModel.v_ui_passwordInput = it},
            label = { Text("Master Password")},
            singleLine = true,
            visualTransformation = if (v_ui_viewModel.v_ui_isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(onClick = {v_ui_viewModel.v_ui_isPasswordVisible = !v_ui_viewModel.v_ui_isPasswordVisible}) {
                    Text(if(v_ui_viewModel.v_ui_isPasswordVisible) "HIDE" else "SHOW")
                }
            }
        )
        //Checks if box is empty
        if (v_ui_viewModel.v_ui_statusMessage.isNotEmpty()) {
            Text(
                text = v_ui_viewModel.v_ui_statusMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
                onClick = { v_ui_viewModel.f_ui_onUnlockedClicked() },
                modifier = Modifier.fillMaxWidth()
        ) {
            Text("Unlock Vault")
        }
    }
}

// Dashboard

@Composable
fun C_ui_DashboardScreen(modifier: Modifier = Modifier, v_ui_viewModel: C_ui_VaultViewModel ){

    Column(
        modifier = modifier.fillMaxSize().padding(17.dp)
    ) {
        Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ShadowVault",
                style = MaterialTheme.typography.titleLarge
            )
            Button(
                onClick = { v_ui_viewModel.f_ui_lockImmediate() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("LOCK VAULT")
            }
        }

        Spacer(modifier = Modifier.height(17.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { v_ui_viewModel.v_ui_selectedChamber = E_ui_VaultChamber.HOT },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (v_ui_viewModel.v_ui_selectedChamber == E_ui_VaultChamber.HOT)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text("🔥 Hot Vault")
            }

            Button(
                onClick = { v_ui_viewModel.v_ui_selectedChamber = E_ui_VaultChamber.COLD },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (v_ui_viewModel.v_ui_selectedChamber == E_ui_VaultChamber.COLD)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text("🧊 Cold Vault 🔒")
            }
        }
        if (v_ui_viewModel.v_ui_selectedChamber == E_ui_VaultChamber.HOT) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                E_ui_HotSection.entries.forEach { v_section ->
                    val v_isSelected = v_ui_viewModel.v_ui_selectedHotSection == v_section
                    TextButton(
                        onClick = { v_ui_viewModel.v_ui_selectedHotSection = v_section },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (v_isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(
                            text = v_section.v_ui_label,
                            style = if (v_isSelected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Active section indicator text
            Text(
                text = "Active Section: ${v_ui_viewModel.v_ui_selectedHotSection.v_ui_label}",
            style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            // Cold Vault Placeholder (Fort Knox)
            Text(
                text = "🔐 Cold Vault Enclave (Padlocked - Step-Up Auth Pending)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        var v_ui_expandedCardId by remember { mutableStateOf<Long?>(null) }

        // Filter items matching the active subsection
        val v_filteredList = v_ui_viewModel.v_ui_credentialsList.filter {
            it.v_ui_section == v_ui_viewModel.v_ui_selectedHotSection
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) { items(v_filteredList) { v_item ->
            C_ui_CredentialCard(
                v_ui_item = v_item,
                v_ui_isExpanded = (v_ui_expandedCardId == v_item.v_ui_id),
                onCardClicked = {
                    // If already open, clicking it closes it (null); otherwise opens it
                    v_ui_expandedCardId = if (v_ui_expandedCardId == v_item.v_ui_id) null else v_item.v_ui_id
                }
            )
        }
        }
    }
}

// Card layout
@Composable
fun C_ui_CredentialCard(
    v_ui_item: C_ui_CredentialItem,
    v_ui_isExpanded: Boolean,
    onCardClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var v_ui_isSecretVisible by remember { mutableStateOf(false) }
    var v_ui_copyCountdown by remember { mutableIntStateOf(0) }

    val v_context = LocalContext.current
    val v_scope = rememberCoroutineScope()

    OutlinedCard(
        onClick = onCardClicked,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = v_ui_item.v_ui_title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = v_ui_item.v_ui_account,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (v_ui_isExpanded) "▲" else "▼",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (v_ui_isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (v_ui_isSecretVisible) v_ui_item.v_ui_secret else "••••••••••••",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { v_ui_isSecretVisible = !v_ui_isSecretVisible }) {
                        Text(if (v_ui_isSecretVisible) "HIDE" else "SHOW")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val v_clipboard = v_context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        v_clipboard.setPrimaryClip(ClipData.newPlainText("secret", v_ui_item.v_ui_secret))

                        v_scope.launch {
                            for (i in 5 downTo 1) {
                                v_ui_copyCountdown = i
                                delay(1000)
                            }
                            v_ui_copyCountdown = 0
                            // Physically wipes the clipboard after 5 seconds
                            v_clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = v_ui_copyCountdown == 0
                ) {
                    Text(
                        if (v_ui_copyCountdown > 0) "Copied ($v_ui_copyCountdown)"
                        else "COPY (5s auto wipe)"
                    )
                }
            }
        }
    }
}
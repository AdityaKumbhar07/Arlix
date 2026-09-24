package com.arlix.svault

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.arlix.svault.crypto.ShadowCryptoProvider
import com.arlix.svault.data.VaultRepositoryImpl
import com.arlix.svault.domain.usecase.LockVaultUseCase
import com.arlix.svault.domain.usecase.UnlockVaultUseCase
import com.arlix.svault.ui.VaultUiState
import com.arlix.svault.ui.VaultViewModel
import com.arlix.svault.ui.screens.AddCredentialScreen
import com.arlix.svault.ui.screens.DashboardScreen
import com.arlix.svault.ui.screens.LockScreen
import com.arlix.svault.ui.theme.ArlixTheme

class MainActivity : ComponentActivity() {

    private val viewModel: VaultViewModel by viewModels {
        val app = application as ArlixApplication
        VaultViewModel.Factory(
            app.unlockUseCase,
            app.lockUseCase,
            app.vaultRepository,
            com.arlix.svault.crypto.SaltGenerator.getSalt(applicationContext)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- OS ARMOR ---

        // [T1] Block screenshots and recent-app carousel snapshots at the OS level.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // [T17] Block Trojan Autofill malware from reading field values via AutofillService
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS

        // [T4] Drop touch events when an invisible overlay is detected on top of the app.
        window.decorView.rootView.filterTouchesWhenObscured = true



        setContent {
            ArlixTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()

                    // coldVaultError is a separate StateFlow — errors from cold vault operations
                    // that should be shown in the Dashboard dialog rather than on the LockScreen.
                    val coldVaultError by viewModel.coldVaultError.collectAsState()

                    Crossfade(targetState = uiState, label = "ScreenTransition") { state ->
                        when (state) {

                            // --- First-launch: vault DB does not exist yet ---
                            is VaultUiState.Setup -> {
                                LockScreen(
                                    uiState = state,
                                    onUnlock = { password ->
                                        viewModel.unlock(password, isColdVault = false)
                                    },
                                    onCreateVault = { password, confirm ->
                                        viewModel.createVault(password, confirm, state.isColdVault)
                                    }
                                )
                            }

                            // --- Lock / in-progress / error — all handled by LockScreen ---
                            is VaultUiState.Locked,
                            is VaultUiState.Unlocking,
                            is VaultUiState.Error -> {
                                LockScreen(
                                    uiState = state,
                                    onUnlock = { password ->
                                        viewModel.unlock(password, isColdVault = false)
                                    },
                                    onCreateVault = { _, _ -> /* Not applicable in Locked state */ }
                                )
                            }

                            // --- Vault is open: show credential dashboard ---
                            is VaultUiState.Unlocked -> {
                                DashboardScreen(
                                    entries = state.entries,
                                    isColdVault = state.isColdVault,
                                    coldVaultExists = viewModel.coldVaultExists(),
                                    // coldVaultError flows inline into the dialog (Bug 1a fix)
                                    coldVaultError = coldVaultError,
                                    onLock = { viewModel.lock() },
                                    onColdVaultUnlock = { coldPassword ->
                                        viewModel.unlock(coldPassword, isColdVault = true)
                                    },
                                    onColdVaultCreate = { coldPassword, confirm ->
                                        viewModel.createVault(coldPassword, confirm, isColdVault = true)
                                    },
                                    onDismissColdVaultError = { viewModel.clearColdVaultError() },
                                    onAddClicked = { viewModel.navigateToAddCredential(state.isColdVault) }
                                )
                            }

                            // --- Add Credential form ---
                            is VaultUiState.AddingCredential -> {
                                AddCredentialScreen(
                                    onSave = { entry -> viewModel.addCredential(entry) },
                                    onCancel = { viewModel.cancelAddCredential(state.isColdVault) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // ProcessLifecycleOwner.ON_STOP is the primary lock signal (see above).
        // onPause() is intentionally left as a no-op — it fires on dialog/IME focus flickers
        // that we explicitly do NOT want to trigger a vault lock.
    }
}

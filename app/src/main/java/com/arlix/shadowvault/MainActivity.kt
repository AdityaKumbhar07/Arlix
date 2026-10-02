package com.arlix.shadowvault

import android.os.Bundle
import android.view.View
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
import androidx.lifecycle.lifecycleScope
import com.arlix.shadowvault.ui.VaultUiState
import com.arlix.shadowvault.ui.VaultViewModel
import com.arlix.shadowvault.ui.screens.LockScreen
import com.arlix.shadowvault.ui.theme.ArlixTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: VaultViewModel by viewModels {
        val app = application as ArlixApplication
        VaultViewModel.Factory(
            app.unlockUseCase,
            app.lockUseCase,
            app.vaultRepository,
            saltProvider = { isCold -> com.arlix.shadowvault.crypto.SaltGenerator.getSalt(applicationContext, isCold) }
        )
    }

        override fun onStop() {
        super.onStop()
        (application as ArlixApplication).lockUseCase.let { lockUseCase ->
            lifecycleScope.launch { lockUseCase() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- OS ARMOR ---

        // [T1] Block screenshots and recent-app carousel snapshots at the OS level.
//        window.setFlags(
//            WindowManager.LayoutParams.FLAG_SECURE,
//            WindowManager.LayoutParams.FLAG_SECURE
//        )

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
                                    onCreateVault = { password, confirm ->
                                        viewModel.createVault(password, confirm, isColdVault = false)
                                    }
                                )
                            }

                            // --- Vault is open: show credential dashboard ---
                            is VaultUiState.Unlocked -> {
                                if (state.isColdVault) {
                                    com.arlix.shadowvault.ui.screens.ColdVaultScreen(
                                        entries = state.entries,
                                        onBack = { viewModel.lock() },
                                        onAddEntry = { entry -> viewModel.addCredential(entry) }
                                    )
                                } else {
                                    val selectedCategory by viewModel.selectedCategory.collectAsState()
                                    com.arlix.shadowvault.ui.screens.HotVaultScreen(
                                        entries = state.entries,
                                        selectedCategory = selectedCategory,
                                        onCategorySelected = { viewModel.setCategoryFilter(it) },
                                        onLock = { viewModel.lock() },
                                        onOpenColdVault = { coldPassword ->
                                            viewModel.unlock(coldPassword, isColdVault = true)
                                        },
                                        onAddEntry = { entry -> viewModel.addCredential(entry) },
                                        coldVaultExists = viewModel.coldVaultExists(),
                                        onColdVaultCreate = { coldPassword, confirm ->
                                            viewModel.createVault(coldPassword, confirm, isColdVault = true)
                                        },
                                        coldVaultError = coldVaultError,
                                        onDismissColdVaultError = { viewModel.clearColdVaultError() }
                                    )
                                }
                            }

                            // --- Add Credential form (no longer used since it's a bottom sheet in VaultScreens) ---
                            is VaultUiState.AddingCredential -> {
                                // Just a fallback, shouldn't be reached
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

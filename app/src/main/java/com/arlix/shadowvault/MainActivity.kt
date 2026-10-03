package com.arlix.shadowvault

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arlix.shadowvault.crypto.SaltGenerator
import com.arlix.shadowvault.ui.VaultUiState
import com.arlix.shadowvault.ui.VaultViewModel
import com.arlix.shadowvault.ui.screens.ColdVaultScreen
import com.arlix.shadowvault.ui.screens.HotVaultScreen
import com.arlix.shadowvault.ui.screens.LockScreen
import com.arlix.shadowvault.ui.theme.ArlixTheme

class MainActivity : ComponentActivity() {

    private val viewModel: VaultViewModel by viewModels {
        val app = application as ArlixApplication
        VaultViewModel.Factory(
            app.unlockUseCase,
            app.lockUseCase,
            app.vaultRepository,
            saltProvider = { isCold -> SaltGenerator.getSalt(applicationContext, isCold) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- OS ARMOR ---

        // [T1] Block screenshots, screen recording and the recents thumbnail.
        // Compose dialogs and bottom sheets inherit this flag from this window by default.
        // See SecurityConfig.screenshotMode for the debug-only screenshot switch.
        if (!SecurityConfig.screenshotMode) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        // [T17] Block autofill services from reading field values.
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS

        // [T4] Drop touches while an overlay covers the app (sheets/dialogs do this themselves).
        window.decorView.rootView.filterTouchesWhenObscured = true

        // Locking is handled in one place: ArlixApplication (ProcessLifecycleOwner + screen-off).

        setContent {
            ArlixTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()
                    val selectedCategory by viewModel.selectedCategory.collectAsState()
                    // Errors from cold vault operations are shown in the Dashboard dialog.
                    val coldVaultError by viewModel.coldVaultError.collectAsState()

                    // Plain `when` (no Crossfade) so the unlocked screen disappears instantly on lock.
                    when (val state = uiState) {

                        is VaultUiState.Setup -> {
                            LockScreen(
                                uiState = state,
                                onUnlock = { password -> viewModel.unlock(password, isColdVault = false) },
                                onCreateVault = { password, confirm ->
                                    viewModel.createVault(password, confirm, state.isColdVault)
                                }
                            )
                        }

                        is VaultUiState.Locked,
                        is VaultUiState.Unlocking,
                        is VaultUiState.Error -> {
                            LockScreen(
                                uiState = state,
                                onUnlock = { password -> viewModel.unlock(password, isColdVault = false) },
                                onCreateVault = { password, confirm ->
                                    viewModel.createVault(password, confirm, isColdVault = false)
                                }
                            )
                        }

                        is VaultUiState.Unlocked -> {
                            if (state.isColdVault) {
                                ColdVaultScreen(
                                    entries = state.entries,
                                    onBack = { viewModel.lock() },
                                    onAddEntry = { entry -> viewModel.addCredential(entry) },
                                    onDeleteEntry = { id -> viewModel.deleteCredential(id) }
                                )
                            } else {
                                HotVaultScreen(
                                    entries = state.entries,
                                    selectedCategory = selectedCategory,
                                    onCategorySelected = { viewModel.setCategoryFilter(it) },
                                    onLock = { viewModel.lock() },
                                    onOpenColdVault = { coldPassword ->
                                        viewModel.unlock(coldPassword, isColdVault = true)
                                    },
                                    onAddEntry = { entry -> viewModel.addCredential(entry) },
                                    onDeleteEntry = { id -> viewModel.deleteCredential(id) },
                                    coldVaultExists = viewModel.coldVaultExists(),
                                    onColdVaultCreate = { coldPassword, confirm ->
                                        viewModel.createVault(coldPassword, confirm, isColdVault = true)
                                    },
                                    coldVaultError = coldVaultError,
                                    onDismissColdVaultError = { viewModel.clearColdVaultError() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

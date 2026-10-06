package com.arlix.shadowvault

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.arlix.shadowvault.crypto.SaltGenerator
import com.arlix.shadowvault.data.BackupFileIo
import com.arlix.shadowvault.ui.MaintenanceViewModel
import com.arlix.shadowvault.ui.VaultUiState
import com.arlix.shadowvault.ui.VaultViewModel
import com.arlix.shadowvault.ui.screens.ColdVaultScreen
import com.arlix.shadowvault.ui.screens.HotVaultScreen
import com.arlix.shadowvault.ui.screens.LockScreen
import com.arlix.shadowvault.ui.screens.MaintenanceSheet
import com.arlix.shadowvault.ui.screens.RestorePassphraseDialog
import com.arlix.shadowvault.ui.theme.ArlixTheme
import kotlinx.coroutines.launch

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

    private val maintenance: MaintenanceViewModel by viewModels {
        val app = application as ArlixApplication
        MaintenanceViewModel.Factory(
            app.changePassphraseUseCase,
            app.lockUseCase,
            app.vaultRepository,
            app.backupCodec,
            saltProvider = { isCold -> SaltGenerator.getSalt(applicationContext, isCold) }
        )
    }

    // Registered here (not inside a composable) so the result is delivered even if the vault
    // locked while the system file picker was open (opening it backgrounds the app).
    private val createBackupFile = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> onBackupLocationChosen(uri) }

    private val openBackupFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> onRestoreFileChosen(uri) }

    /** The backup is already encrypted; this only stores it and verifies it can be read back. */
    private fun onBackupLocationChosen(uri: Uri?) {
        val data = maintenance.takePendingBackup()
        if (uri == null || data == null) {
            maintenance.report("Backup cancelled.")
            return
        }
        lifecycleScope.launch {
            val ok = BackupFileIo.write(contentResolver, uri, data)
            maintenance.report(
                if (ok) "Backup saved and verified."
                else "Backup could not be verified. Do not rely on it; try again."
            )
        }
    }

    private fun onRestoreFileChosen(uri: Uri?) {
        if (uri == null) return
        lifecycleScope.launch {
            maintenance.onRestoreFilePicked(BackupFileIo.read(contentResolver, uri))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- OS ARMOR ---

        // [T1] Block screenshots, screen recording and the recents thumbnail. Compose dialogs
        // and bottom sheets inherit this flag from this window by default.
        // See SecurityConfig.screenshotMode for the debug-only screenshot switch.
        if (!SecurityConfig.screenshotMode) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        // [T17] Block autofill services from reading field values (sheets and dialogs do this
        // themselves in ObscuredTouchGuard, because they are separate windows).
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS

        // [T4] Drop touches while another app's overlay covers the app.
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
                    val coldVaultError by viewModel.coldVaultError.collectAsState()
                    val coldVaultBusy by viewModel.coldVaultBusy.collectAsState()
                    val userMessage by viewModel.userMessage.collectAsState()
                    val working by maintenance.working.collectAsState()
                    val restoreTarget by maintenance.restoreTarget.collectAsState()
                    val context = LocalContext.current

                    var toolsOpen by remember { mutableStateOf(false) }
                    val isUnlocked = uiState is VaultUiState.Unlocked

                    // The tools sheet must never reappear by itself after a lock.
                    LaunchedEffect(isUnlocked) { if (!isUnlocked) toolsOpen = false }

                    // One-shot messages for failures while unlocked (e.g. storage full).
                    LaunchedEffect(userMessage) {
                        userMessage?.let {
                            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                            viewModel.clearUserMessage()
                        }
                    }

                    // Results of change-passphrase / backup / restore.
                    LaunchedEffect(Unit) {
                        maintenance.messages.collect {
                            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                        }
                    }

                    // The encrypted backup is ready: let the user choose where to store it.
                    LaunchedEffect(Unit) {
                        maintenance.exportReady.collect { fileName ->
                            toolsOpen = false
                            createBackupFile.launch(fileName)
                        }
                    }

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
                                    onDeleteEntry = { id -> viewModel.deleteCredential(id) },
                                    onOpenTools = { toolsOpen = true }
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
                                    onDismissColdVaultError = { viewModel.clearColdVaultError() },
                                    coldVaultBusy = coldVaultBusy,
                                    onOpenTools = { toolsOpen = true }
                                )
                            }

                            if (toolsOpen) {
                                MaintenanceSheet(
                                    isColdVault = state.isColdVault,
                                    working = working,
                                    onDismiss = { toolsOpen = false },
                                    onChangePassphrase = { current, new, confirm ->
                                        maintenance.changePassphrase(current, new, confirm, state.isColdVault)
                                    },
                                    onCreateBackup = { passphrase, confirm ->
                                        maintenance.prepareBackup(passphrase, confirm, state.isColdVault)
                                    },
                                    onRestore = {
                                        toolsOpen = false
                                        maintenance.beginRestore(state.isColdVault)
                                        openBackupFile.launch(arrayOf("*/*"))
                                    }
                                )
                            }

                            // Shown only in the vault the restore was started from.
                            if (restoreTarget == state.isColdVault) {
                                RestorePassphraseDialog(
                                    isColdVault = state.isColdVault,
                                    working = working,
                                    onConfirm = { passphrase -> maintenance.restoreBackup(passphrase) },
                                    onCancel = { maintenance.cancelRestore() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

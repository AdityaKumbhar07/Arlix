package com.arlix.shadowvault.ui

import com.arlix.shadowvault.domain.VaultEntry
import com.arlix.shadowvault.domain.VaultFileException
import com.arlix.shadowvault.domain.usecase.LockVaultUseCase
import com.arlix.shadowvault.domain.usecase.UnlockVaultUseCase
import com.arlix.shadowvault.testing.FakeVaultRepository
import com.arlix.shadowvault.testing.FastCrypto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModelHardeningTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: FakeVaultRepository
    private lateinit var crypto: FastCrypto

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeVaultRepository()
        crypto = FastCrypto()
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm(salt: () -> ByteArray = { ByteArray(16) }) = VaultViewModel(
        unlockVaultUseCase = UnlockVaultUseCase(crypto, repo, dispatcher),
        lockVaultUseCase = LockVaultUseCase(repo),
        vaultRepository = repo,
        saltProvider = { _ -> salt() },
        ioDispatcher = dispatcher
    )

    private fun pw(text: String) = text.toCharArray()

    private fun entry(password: String = "p4ss") = VaultEntry(
        title = "t", username = "u", notes = "", passwordSecret = password.toCharArray()
    )

    @Test fun unlockShowsTheVault() {
        val vm = newVm()
        vm.unlock(pw("secret"))
        assertTrue(vm.uiState.value is VaultUiState.Unlocked)
        assertTrue(repo.isVaultOpen())
    }

    @Test fun wrongPassphraseShowsAnErrorAndLeavesTheVaultClosed() {
        repo.failOpenWith = IllegalStateException("bad key")
        val vm = newVm()
        vm.unlock(pw("wrong"))
        val state = vm.uiState.value
        assertTrue(state is VaultUiState.Error)
        assertTrue((state as VaultUiState.Error).message.contains("Incorrect"))
        assertFalse(repo.isVaultOpen())
    }

    @Test fun damagedSaltFileHasItsOwnMessage() {
        val vm = newVm(salt = { throw VaultFileException("missing") })
        vm.unlock(pw("secret"))
        val state = vm.uiState.value
        assertTrue(state is VaultUiState.Error)
        assertTrue((state as VaultUiState.Error).message.contains("key file"))
    }

    @Test fun failedSaveKeepsTheVaultUnlockedAndShowsAMessage() {
        val vm = newVm()
        vm.unlock(pw("secret"))
        repo.failAdd = true
        vm.addCredential(entry())
        assertTrue("must stay unlocked, not show a fake lock screen", vm.uiState.value is VaultUiState.Unlocked)
        assertNotNull(vm.userMessage.value)
        assertTrue(repo.isVaultOpen())
    }

    @Test fun failedReadPerformsARealLock() {
        repo.failFlowWith = IllegalStateException("db gone")
        val vm = newVm()
        vm.unlock(pw("secret"))
        assertTrue(vm.uiState.value is VaultUiState.Error)
        assertFalse("the lock screen must mean the vault is closed", repo.isVaultOpen())
    }

    @Test fun lockClosesTheVaultAgainAndShowsTheLockScreen() {
        val vm = newVm()
        vm.unlock(pw("secret"))
        vm.lock()
        assertTrue(vm.uiState.value is VaultUiState.Locked)
        assertFalse(repo.isVaultOpen())
        assertTrue("safety close after the lock event", repo.closeCalls >= 2)
    }

    @Test fun lockWipesThePasswordsLoadedInMemory() {
        val loaded = entry("loaded-password")
        repo.seed(loaded)
        val vm = newVm()
        vm.unlock(pw("secret"))
        assertTrue("entry should be on screen", (vm.uiState.value as VaultUiState.Unlocked).entries.isNotEmpty())
        vm.lock()
        assertTrue("password must be zeroed on lock", loaded.passwordSecret.all { it == '\u0000' })
    }

    @Test fun savedEntryPasswordIsWipedAfterSaving() {
        val vm = newVm()
        vm.unlock(pw("secret"))
        val toSave = entry("to-save")
        vm.addCredential(toSave)
        assertTrue(toSave.passwordSecret.all { it == '\u0000' })
    }

    @Test fun secondColdAttemptIsIgnoredWhileTheFirstRuns() {
        val vm = newVm()
        vm.unlock(pw("hot"))
        crypto.gate = CompletableDeferred() // hold the next key derivation

        vm.unlock(pw("cold-1"), isColdVault = true)
        assertTrue(vm.coldVaultBusy.value)
        vm.unlock(pw("cold-2"), isColdVault = true) // must be ignored
        crypto.gate.complete(Unit)

        assertEquals("only one cold open", 1, repo.openCalls.count { it })
        assertFalse(vm.coldVaultBusy.value)
        assertTrue((vm.uiState.value as VaultUiState.Unlocked).isColdVault)
    }
}

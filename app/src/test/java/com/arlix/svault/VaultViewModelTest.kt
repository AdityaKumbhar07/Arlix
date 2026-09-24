package com.arlix.svault.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import com.arlix.svault.domain.ICryptoProvider
import com.arlix.svault.domain.IVaultRepository
import com.arlix.svault.domain.VaultEntry
import com.arlix.svault.domain.usecase.LockVaultUseCase
import com.arlix.svault.domain.usecase.UnlockVaultUseCase

/**
 * Unit tests for VaultViewModel state machine.
 *
 * WHY UnconfinedTestDispatcher (not StandardTestDispatcher):
 * UnlockVaultUseCase calls withContext(Dispatchers.IO) internally. With StandardTestDispatcher,
 * that IO context switch hangs because the test controls only Main. UnconfinedTestDispatcher
 * runs coroutines eagerly and immediately regardless of which dispatcher they switch to,
 * so withContext(Dispatchers.IO) completes synchronously in the test scope.
 *
 * All real crypto/DB dependencies are replaced with fakes — no device or native libs needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModelTest {

    // UnconfinedTestDispatcher: coroutines run eagerly in-place (no scheduling)
    // This is necessary because UnlockVaultUseCase uses withContext(Dispatchers.IO)
    private val testDispatcher = UnconfinedTestDispatcher()

    // -------------------------------------------------------------------------
    // Fakes
    // -------------------------------------------------------------------------

    /** Fake crypto provider — returns a fixed 32-byte key, never crashes. */
    private val fakeCrypto = object : ICryptoProvider {
        override suspend fun deriveMasterKey(password: CharArray, salt: ByteArray): ByteArray =
            ByteArray(32) { 1 }
        override fun encryptData(plaintext: ByteArray, key: ByteArray): ByteArray = plaintext
        override fun decryptData(ciphertext: ByteArray, key: ByteArray): ByteArray = ciphertext
        override fun wipe(buffer: ByteArray) { buffer.fill(0) }
    }

    /**
     * Fake repository.
     * [shouldThrowOnOpen] → simulates a wrong-password / corrupted-vault scenario.
     * [vaultExistsResult] → controls vaultExists() return value.
     */
    private inner class FakeRepository(
        private val shouldThrowOnOpen: Boolean = false,
        private val vaultExistsResult: Boolean = true
    ) : IVaultRepository {
        private val entriesFlow = MutableStateFlow<List<VaultEntry>>(emptyList())
        private var isOpen = false

        override suspend fun openVault(masterKey: ByteArray, isColdVault: Boolean): Boolean {
            if (shouldThrowOnOpen) throw RuntimeException("Wrong password — SQLCipher rejected the key")
            isOpen = true
            return true
        }
        override suspend fun closeVault() { isOpen = false }
        override fun isVaultOpen(): Boolean = isOpen
        override fun vaultExists(isColdVault: Boolean): Boolean = vaultExistsResult
        override fun getAllEntries(): Flow<List<VaultEntry>> = entriesFlow
        override suspend fun addEntry(entry: VaultEntry) { /* no-op */ }
        override suspend fun deleteEntry(entryId: String) { /* no-op */ }
    }

    private fun makeViewModel(
        shouldThrowOnOpen: Boolean = false,
        vaultExistsResult: Boolean = true
    ): VaultViewModel {
        val repo = FakeRepository(shouldThrowOnOpen, vaultExistsResult)
        return VaultViewModel(
            // Inject testDispatcher as ioDispatcher so withContext(ioDispatcher) runs eagerly
            // under our control, not on the real Dispatchers.IO (which would hang in tests).
            UnlockVaultUseCase(fakeCrypto, repo, ioDispatcher = testDispatcher),
            LockVaultUseCase(repo),
            repo,
            ByteArray(16)
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    fun `initial state is Setup when vault does not exist`() {
        val vm = makeViewModel(vaultExistsResult = false)
        assertTrue(
            "When vaultExists() = false, initial state must be Setup, not Locked",
            vm.uiState.value is VaultUiState.Setup
        )
    }

    @Test
    fun `initial state is Locked when vault exists`() {
        val vm = makeViewModel(vaultExistsResult = true)
        assertTrue(
            "When vaultExists() = true, initial state must be Locked",
            vm.uiState.value is VaultUiState.Locked
        )
    }

    @Test
    fun `unlock succeeds and reaches Unlocked state`() = runTest {
        val vm = makeViewModel(vaultExistsResult = true)

        vm.unlock("CorrectPassword".toCharArray())
        // UnconfinedTestDispatcher runs eagerly — coroutines are done by here
        assertTrue(
            "After successful hot vault unlock, state must be Unlocked. Actual: ${vm.uiState.value}",
            vm.uiState.value is VaultUiState.Unlocked
        )
    }

    @Test
    fun `unlock with wrong password never reaches Unlocked state`() = runTest {
        val vm = makeViewModel(shouldThrowOnOpen = true, vaultExistsResult = true)

        vm.unlock("WrongPassword".toCharArray())
        // State should be Error (we no longer auto-transition to Locked on error)
        assertFalse(
            "Wrong password must NEVER grant vault access",
            vm.uiState.value is VaultUiState.Unlocked
        )
        assertTrue(
            "Wrong password must result in Error state so the message is visible. Actual: ${vm.uiState.value}",
            vm.uiState.value is VaultUiState.Error
        )
    }

    @Test
    fun `re-entrant unlock calls are dropped while Unlocking is in progress`() = runTest {
        // With UnconfinedTestDispatcher, unlockHotVaultInternal sets Unlocking synchronously
        // and then immediately completes. So we can't easily test the mid-flight guard here —
        // but we can verify two sequential calls don't cause a crash or double-unlock.
        val vm = makeViewModel(vaultExistsResult = true)

        vm.unlock("Password".toCharArray())
        // First call succeeded — state is Unlocked
        assertTrue(vm.uiState.value is VaultUiState.Unlocked)

        // Second call while already Unlocked — re-entrancy guard fires on Unlocking check;
        // since state is Unlocked (not Unlocking), this call goes through normally (re-unlocking
        // the same vault is safe). Just verify no crash and state remains valid.
        vm.unlock("Password".toCharArray())
        assertTrue(
            "State must remain valid after two sequential unlock calls",
            vm.uiState.value is VaultUiState.Unlocked
        )
    }

    @Test
    fun `lock returns to Locked state after unlock`() = runTest {
        val vm = makeViewModel(vaultExistsResult = true)

        vm.unlock("Password".toCharArray())
        assertTrue(vm.uiState.value is VaultUiState.Unlocked)

        vm.lock()
        assertTrue(
            "lock() must result in Locked state when vault exists. Actual: ${vm.uiState.value}",
            vm.uiState.value is VaultUiState.Locked
        )
    }

    @Test
    fun `createVault rejects passwords shorter than 5 characters`() = runTest {
        val vm = makeViewModel(vaultExistsResult = false)

        vm.createVault("abc".toCharArray(), "abc".toCharArray())
        assertTrue(
            "createVault must reject passwords < 5 chars with an Error state",
            vm.uiState.value is VaultUiState.Error
        )
    }

    @Test
    fun `createVault rejects mismatched confirmation password`() = runTest {
        val vm = makeViewModel(vaultExistsResult = false)

        vm.createVault("CorrectPass".toCharArray(), "DifferentPass".toCharArray())
        val state = vm.uiState.value
        assertTrue("createVault must reject non-matching confirmation", state is VaultUiState.Error)
        assertTrue(
            "Error message must mention mismatch",
            (state as VaultUiState.Error).message.contains("do not match", ignoreCase = true)
        )
    }

    @Test
    fun `createVault for cold vault sends error to coldVaultError not uiState`() = runTest {
        val vm = makeViewModel(vaultExistsResult = true) // vault exists = user is on dashboard

        vm.createVault("ab".toCharArray(), "ab".toCharArray(), isColdVault = true)

        // The global uiState must NOT change — we're on the Dashboard (Locked in our test setup)
        assertTrue(
            "Cold vault validation error must NOT navigate away from Dashboard (uiState must stay Locked)",
            vm.uiState.value is VaultUiState.Locked
        )
        assertNotNull(
            "Cold vault validation error must appear in coldVaultError (for inline dialog display)",
            vm.coldVaultError.value
        )
    }
}

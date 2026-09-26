package com.arlix.shadowvault

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arlix.shadowvault.crypto.ShadowCryptoProvider
import com.arlix.shadowvault.data.VaultRepositoryImpl
import com.arlix.shadowvault.domain.VaultEntry
import com.arlix.shadowvault.domain.usecase.LockVaultUseCase
import com.arlix.shadowvault.domain.usecase.UnlockVaultUseCase
import com.arlix.shadowvault.ui.VaultViewModel
import com.arlix.shadowvault.ui.VaultUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented (on-device) integration test.
 * Verifies that entering a wrong password does not crash the app —
 * it must land in VaultUiState.Error gracefully.
 *
 * This is the regression test for "wrong-password crash" behavior.
 * Run with: ./gradlew :app:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class UnlockCrashTest {

    @Test
    fun testUnlockWithWrongPasswordDoesNotCrash() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cryptoProvider = ShadowCryptoProvider()
        val repository = VaultRepositoryImpl(context)
        val unlockUseCase = UnlockVaultUseCase(cryptoProvider, repository)
        val lockUseCase = LockVaultUseCase(repository)
        val salt = ByteArray(16) { 1 }

        val viewModel = VaultViewModel(unlockUseCase, lockUseCase, repository, salt)

        // Step 1: Create the vault with a known correct password
        unlockUseCase.invoke("CorrectPassword".toCharArray(), salt, false)

        // Step 2: Insert a dummy credential so the DB is non-empty
        // VaultEntry constructor: (id, title, username, notes, passwordSecret, createdAt, modifiedAt)
        // All positional args must match this order exactly.
        repository.addEntry(
            VaultEntry(
                id = "test-entry-1",
                title = "Test Title",
                username = "testuser",
                notes = "Test notes",
                passwordSecret = "TestPass".toCharArray(),
                createdAt = 0L,
                modifiedAt = 0L
            )
        )

        // Step 3: Lock the vault
        lockUseCase.invoke()

        // Step 4: Attempt unlock with an INCORRECT password
        viewModel.unlock("WrongPassword".toCharArray(), false)

        // Step 5: Wait for the Argon2id derivation + SQLCipher attempt to complete
        // (~500ms for Argon2id + some overhead — 3 seconds gives plenty of headroom)
        delay(3000)

        // Step 6: Verify we landed in Error state, not crashed
        assertTrue(
            "Unlock with wrong password must result in Error state, not a crash or Unlocked state. " +
            "Actual state: ${viewModel.uiState.value}",
            viewModel.uiState.value is VaultUiState.Error
        )
    }
}

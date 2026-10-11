package com.arlix.shadowvault.domain.usecase

import com.arlix.shadowvault.testing.FakeVaultRepository
import com.arlix.shadowvault.testing.FastCrypto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UnlockVaultUseCaseTest {

    private val salt = ByteArray(16)

    @Test fun passphraseIsZeroedAfterUnlock() = runTest {
        val password = "secret".toCharArray()
        val useCase = UnlockVaultUseCase(FastCrypto(), FakeVaultRepository(), UnconfinedTestDispatcher(testScheduler))
        useCase(password, salt)
        assertTrue("passphrase must be zeroed", password.all { it == '\u0000' })
    }

    @Test fun passphraseIsZeroedEvenWhenOpeningFails() = runTest {
        val password = "secret".toCharArray()
        val repo = FakeVaultRepository().apply { failOpenWith = IllegalStateException("wrong key") }
        val useCase = UnlockVaultUseCase(FastCrypto(), repo, UnconfinedTestDispatcher(testScheduler))
        try {
            useCase(password, salt)
            fail("expected the open to fail")
        } catch (e: IllegalStateException) {
        }
        assertTrue("passphrase must be zeroed", password.all { it == '\u0000' })
    }

    @Test fun derivedKeyIsWipedAfterUnlock() = runTest {
        val crypto = FastCrypto()
        val useCase = UnlockVaultUseCase(crypto, FakeVaultRepository(), UnconfinedTestDispatcher(testScheduler))
        useCase("secret".toCharArray(), salt)
        val key = crypto.lastKey
        assertNotNull(key)
        assertTrue("derived key must be wiped", key!!.all { it == 0.toByte() })
    }

    @Test fun cancelledUnlockClosesWhatItOpened() = runTest {
        val repo = FakeVaultRepository().apply { openGate = CompletableDeferred() }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val useCase = UnlockVaultUseCase(FastCrypto(), repo, dispatcher)
        val job = launch(dispatcher) { useCase("secret".toCharArray(), salt) }
        assertTrue("vault should be in the middle of opening", repo.isVaultOpen())
        job.cancelAndJoin()
        assertFalse("a cancelled unlock must not leave the vault open", repo.isVaultOpen())
    }
}

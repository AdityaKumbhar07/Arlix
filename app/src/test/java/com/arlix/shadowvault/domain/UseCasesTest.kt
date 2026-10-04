package com.arlix.shadowvault.domain

import app.cash.turbine.test
import com.arlix.shadowvault.domain.usecase.LockVaultUseCase
import com.arlix.shadowvault.domain.usecase.UnlockVaultUseCase
import com.arlix.shadowvault.testutil.FakeCryptoProvider
import com.arlix.shadowvault.testutil.FakeVaultRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UnlockVaultUseCaseTest {
    private val salt = ByteArray(16) { 5 }
    private val crypto = FakeCryptoProvider()
    private val repo = FakeVaultRepository()

    private fun TestScope.useCase() = UnlockVaultUseCase(crypto, repo, StandardTestDispatcher(testScheduler))
    private fun zeroed(c: CharArray) = c.all { it == '\u0000' }

    @Test fun successOpensVaultAndWipesPasswordAndKey() = runTest {
        val expected = FakeCryptoProvider().deriveMasterKey("secret123".toCharArray(), salt)
        val pw = "secret123".toCharArray()
        assertTrue(useCase()(pw, salt, false))
        assertTrue(zeroed(pw))
        assertArrayEquals(expected, repo.storedKey(false))
        assertEquals(1, crypto.wipedBuffers.size)
        assertTrue(crypto.wipedBuffers[0].all { it == 0.toByte() })
    }

    @Test fun coldFlagIsForwarded() = runTest {
        useCase()("coldpass1".toCharArray(), salt, true)
        assertEquals(true, repo.openIsCold)
    }

    @Test fun wrongKeyThrowsAndStillWipesEverything() = runTest {
        repo.seedVault(false, FakeCryptoProvider().deriveMasterKey("right-pass".toCharArray(), salt))
        val pw = "wrong-pass".toCharArray()
        try { useCase()(pw, salt, false); fail("expected failure") } catch (e: IllegalStateException) { }
        assertTrue(zeroed(pw))
        assertEquals(1, crypto.wipedBuffers.size)
        assertFalse(repo.isVaultOpen())
    }

    @Test fun derivationFailureWipesPasswordAndNeverOpens() = runTest {
        crypto.failWith = IllegalArgumentException("boom")
        val pw = "whatever1".toCharArray()
        try { useCase()(pw, salt, false); fail("expected failure") } catch (e: IllegalArgumentException) { }
        assertTrue(zeroed(pw))
        assertEquals(0, repo.openCalls)
        assertTrue(crypto.wipedBuffers.isEmpty())
    }

    @Test fun cancellationDuringDerivationWipesPassword() = runTest {
        crypto.gate = CompletableDeferred()
        val pw = "slow-unlock".toCharArray()
        val uc = useCase()
        val job = launch { uc(pw, salt, false) }
        advanceUntilIdle()
        job.cancelAndJoin()
        assertTrue(zeroed(pw))
        assertEquals(0, repo.openCalls)
    }
}

class LockVaultUseCaseTest {
    @Test fun lockClosesVaultAndEmitsEvent() = runTest {
        val repo = FakeVaultRepository()
        repo.openVault(ByteArray(32), false)
        val uc = LockVaultUseCase(repo)
        uc.lockEvents.test {
            uc()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(repo.isVaultOpen())
    }

    @Test fun lockingTwiceOrWhenAlreadyClosedIsHarmless() = runTest {
        val repo = FakeVaultRepository()
        val uc = LockVaultUseCase(repo)
        uc(); uc()
        assertFalse(repo.isVaultOpen())
    }

    @Test fun lockWithNoSubscriberDoesNotCrash() = runTest {
        LockVaultUseCase(FakeVaultRepository())()
    }
}

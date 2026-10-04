package com.arlix.shadowvault.testutil

import com.arlix.shadowvault.domain.VaultEntry
import com.arlix.shadowvault.domain.usecase.LockVaultUseCase
import com.arlix.shadowvault.domain.usecase.UnlockVaultUseCase
import com.arlix.shadowvault.ui.VaultUiState
import com.arlix.shadowvault.ui.VaultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before

/**
 * Shared harness. The ViewModel reads the salt on the real Dispatchers.IO, so tests poll
 * (advance the test scheduler, sleep briefly) instead of relying on virtual time alone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class VaultViewModelTestBase {

    protected val dispatcher = StandardTestDispatcher()
    protected val hotSalt = ByteArray(16) { 1 }
    protected val coldSalt = ByteArray(16) { 2 }
    protected lateinit var crypto: FakeCryptoProvider
    protected lateinit var repo: FakeVaultRepository
    protected lateinit var vm: VaultViewModel

    @Before
    fun baseSetUp() {
        Dispatchers.setMain(dispatcher)
        crypto = FakeCryptoProvider()
        repo = FakeVaultRepository()
    }

    @After
    fun baseTearDown() {
        Dispatchers.resetMain()
    }

    protected fun newVm(): VaultViewModel {
        val unlock = UnlockVaultUseCase(crypto, repo, dispatcher)
        val lock = LockVaultUseCase(repo)
        vm = VaultViewModel(
            unlockVaultUseCase = unlock,
            lockVaultUseCase = lock,
            repository = repo,
            saltProvider = { isCold -> if (isCold) coldSalt else hotSalt },
            ioDispatcher = dispatcher
        )
        pump()
        return vm
    }

    protected fun pump() = dispatcher.scheduler.advanceUntilIdle()

    protected fun waitFor(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (true) {
            pump()
            if (condition()) return
            if (System.nanoTime() > deadline) fail("Condition not met in ${timeoutMs}ms. State=${vm.uiState.value}")
            Thread.sleep(5)
        }
    }

    /** Lets any late background work finish, for "nothing else happens" assertions. */
    protected fun settle(ms: Long = 80) {
        val end = System.nanoTime() + ms * 1_000_000
        while (System.nanoTime() < end) {
            pump()
            Thread.sleep(5)
        }
        pump()
    }

    protected fun key(password: String, cold: Boolean): ByteArray = runBlocking {
        FakeCryptoProvider().deriveMasterKey(password.toCharArray(), if (cold) coldSalt else hotSalt)
    }

    protected fun seed(isCold: Boolean, password: String, entries: List<VaultEntry> = emptyList()) =
        repo.seedVault(isCold, key(password, isCold), entries)

    protected fun makeEntries(n: Int, prefix: String = "Site"): List<VaultEntry> {
        val cats = listOf("General", "Finance", "Work")
        return (0 until n).map { i ->
            VaultEntry(
                id = "id-$prefix-$i",
                title = "$prefix-${i.toString().padStart(6, '0')}",
                username = "user$i@example.com",
                notes = "note $i",
                passwordSecret = "pw-$i-Ää😀".toCharArray(),
                category = cats[i % 3]
            )
        }
    }

    protected fun unlockHot(pw: String): CharArray = pw.toCharArray().also { vm.unlock(it, false) }
    protected fun unlockCold(pw: String): CharArray = pw.toCharArray().also { vm.unlock(it, true) }

    protected fun state(): VaultUiState = vm.uiState.value
    protected fun unlocked(): VaultUiState.Unlocked = vm.uiState.value as VaultUiState.Unlocked
    protected fun waitUnlocked(cold: Boolean = false, timeoutMs: Long = 5_000) =
        waitFor(timeoutMs) { (state() as? VaultUiState.Unlocked)?.isColdVault == cold }
    protected fun waitLocked() = waitFor { state() is VaultUiState.Locked }
    protected fun waitError() = waitFor { state() is VaultUiState.Error }

    protected fun isZeroed(a: CharArray) = a.all { it == '\u0000' }
    protected fun hasSecret(a: CharArray) = a.any { it != '\u0000' }
}

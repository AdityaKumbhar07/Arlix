package com.arlix.shadowvault

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arlix.shadowvault.crypto.SaltGenerator
import com.arlix.shadowvault.crypto.ShadowCryptoProvider
import com.arlix.shadowvault.data.VaultRepositoryImpl
import com.arlix.shadowvault.domain.VaultEntry
import com.arlix.shadowvault.domain.usecase.LockVaultUseCase
import com.arlix.shadowvault.domain.usecase.UnlockVaultUseCase
import com.arlix.shadowvault.testutil.VaultTestFiles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Rule

@RunWith(AndroidJUnit4::class)
class EndToEndVaultTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repo: VaultRepositoryImpl
    private lateinit var unlock: UnlockVaultUseCase
    private lateinit var lock: LockVaultUseCase

    @Before fun setUp() {
        VaultTestFiles.wipeAll(ctx)
        repo = VaultRepositoryImpl(ctx)
        unlock = UnlockVaultUseCase(ShadowCryptoProvider(), repo)
        lock = LockVaultUseCase(repo)
    }
    @After fun tearDown() { runBlocking { repo.closeVault() }; VaultTestFiles.wipeAll(ctx) }

    private suspend fun open(pw: String, cold: Boolean = false): Boolean {
        val chars = pw.toCharArray()
        val ok = unlock(chars, SaltGenerator.getSalt(ctx, cold), cold)
        assertTrue("password array not zeroed", chars.all { it == '\u0000' })
        return ok
    }
    private suspend fun openFails(pw: String, cold: Boolean = false) {
        val chars = pw.toCharArray()
        try {
            unlock(chars, SaltGenerator.getSalt(ctx, cold), cold)
            fail("expected failure")
        } catch (e: Exception) {
            assertTrue("password array not zeroed after failure", chars.all { it == '\u0000' })
        }
    }
    private fun e(title: String, pw: String) = VaultEntry(title = title, username = "u", notes = "n", passwordSecret = pw.toCharArray())

    @Test fun createAddLockWrongPasswordThenRightPassword() = runBlocking {
        assertFalse(repo.vaultExists())
        assertTrue(open("correct horse battery"))
        repo.addEntry(e("Bank", "hunter2!"))
        lock(); assertFalse(repo.isVaultOpen())
        openFails("wrong password"); assertFalse(repo.isVaultOpen())
        assertTrue(open("correct horse battery"))
        val r = repo.getAllEntries().first().single()
        assertEquals("Bank", r.title); assertEquals("hunter2!", String(r.passwordSecret))
    }

    @Test fun survivesProcessRestartSimulation() = runBlocking {
        open("persist-me-123"); repeat(100) { repo.addEntry(e("S$it", "pw$it")) }; lock()
        val fresh = VaultRepositoryImpl(ctx)
        val freshUnlock = UnlockVaultUseCase(ShadowCryptoProvider(), fresh)
        freshUnlock("persist-me-123".toCharArray(), SaltGenerator.getSalt(ctx, false), false)
        assertEquals(100, fresh.getAllEntries().first().size)
        fresh.closeVault()
    }

    @Test fun coldVaultFlowKeepsHotOpenOnWrongKey() = runBlocking {
        open("hot-passphrase"); repo.addEntry(e("HotBank", "h1"))
        assertTrue(open("cold-passphrase", cold = true))      // creates cold, closes hot
        assertEquals(0, repo.getAllEntries().first().size)
        repo.addEntry(e("ColdSeed", "c1")); lock()

        assertTrue(open("hot-passphrase"))
        assertEquals("HotBank", repo.getAllEntries().first().single().title)
        openFails("not-the-cold-key", cold = true)
        assertTrue(repo.isVaultOpen())
        assertEquals("HotBank", repo.getAllEntries().first().single().title)

        assertTrue(open("cold-passphrase", cold = true))
        assertEquals("ColdSeed", repo.getAllEntries().first().single().title)
    }

    @Test fun emojiAndLongPassphrasesWork() = runBlocking {
        listOf("😀😀😀😀😀😀", "x".repeat(300), "pässwörd-密码").forEach { pw ->
            VaultTestFiles.wipeAll(ctx)
            assertTrue(open(pw)); lock()
            openFails(pw + "!"); assertTrue(open(pw)); lock()
        }
    }

    @Test fun twentyLockUnlockCyclesWith200Entries() = runBlocking {
        open("cycle-passphrase"); repeat(200) { repo.addEntry(e("S$it", "pw$it")) }; lock()
        repeat(20) { assertTrue(open("cycle-passphrase")); assertEquals(200, repo.getAllEntries().first().size); lock() }
    }
}

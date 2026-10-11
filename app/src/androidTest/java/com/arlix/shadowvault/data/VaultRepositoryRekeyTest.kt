package com.arlix.shadowvault.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arlix.shadowvault.domain.VaultEntry
import com.arlix.shadowvault.domain.WrongPassphraseException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultRepositoryRekeyTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val stamp = System.nanoTime()
    private val hotName = "test_hot_$stamp.db"   // never the real vault file names
    private val coldName = "test_cold_$stamp.db"
    private lateinit var repo: VaultRepositoryImpl

    private val keyA = ByteArray(32) { 1 }
    private val keyB = ByteArray(32) { 2 }
    private val keyC = ByteArray(32) { 3 }

    @Before fun setUp() {
        repo = VaultRepositoryImpl(context, hotName, coldName)
    }

    @After fun tearDown() = runBlocking {
        repo.closeVault()
        listOf(hotName, coldName).forEach { name ->
            context.deleteDatabase(name)
            context.deleteDatabase("$name.rekey")
        }
    }

    private fun entry(id: String, title: String, password: String, category: String = "General", notes: String = "n") =
        VaultEntry(
            id = id, title = title, username = "user-$id", notes = notes,
            passwordSecret = password.toCharArray(), category = category,
            createdAt = 10L, modifiedAt = 20L
        )

    private inline fun <reified T : Throwable> expectFailure(block: () -> Unit): T {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return e
            throw e
        }
        fail("Expected ${T::class.java.simpleName}")
        throw AssertionError()
    }

    @Test fun wrongKeyFailsAndLeavesTheFileUntouched() = runBlocking {
        repo.openVault(keyA, false)
        repo.addEntry(entry("1", "One", "pw1"))
        repo.closeVault()
        val file = context.getDatabasePath(hotName)
        val size = file.length()
        val modified = file.lastModified()

        expectFailure<Exception> { repo.openVault(keyB, false) }

        assertTrue("the database file must still exist", file.exists())
        assertEquals(size, file.length())
        assertEquals(modified, file.lastModified())
        repo.openVault(keyA, false)
        assertEquals(1, repo.readAllEntries().size)
    }

    @Test fun listIsSortedCaseInsensitively() = runBlocking {
        repo.openVault(keyA, false)
        listOf("Zoom", "amazon", "Apple").forEachIndexed { i, t -> repo.addEntry(entry("$i", t, "p")) }
        assertEquals(listOf("amazon", "Apple", "Zoom"), repo.readAllEntries().map { it.title })
    }

    @Test fun coldEntriesHaveNoCategoryAndHotEntriesAlwaysDo() = runBlocking {
        repo.openVault(keyA, true)
        repo.addEntry(entry("c1", "Cold", "p", category = "General"))
        assertEquals("", repo.readAllEntries().single().category)

        repo.openVault(keyB, false) // swaps to the hot vault
        repo.addEntry(entry("h1", "Hot", "p", category = ""))
        assertEquals("General", repo.readAllEntries().single().category)
    }

    @Test fun addEntriesStoresTheWholeBatch() = runBlocking {
        repo.openVault(keyA, false)
        repo.addEntries(listOf(entry("1", "A", "p"), entry("2", "B", "p"), entry("3", "C", "p")))
        assertEquals(3, repo.readAllEntries().size)
    }

    @Test fun changePassphraseKeepsEverythingAndSwapsTheKey() = runBlocking {
        val originals = listOf(
            entry("1", "Zoom", "pässword🙂"),
            entry("2", "amazon", "p2", category = "Finance"),
            entry("3", "Apple", "x".repeat(256), notes = "n".repeat(4000))
        )
        repo.openVault(keyA, false)
        originals.forEach { repo.addEntry(it) }

        repo.changePassphrase(keyA, keyB, false)

        assertFalse("the vault is closed after a change", repo.isVaultOpen())
        expectFailure<Exception> { repo.openVault(keyA, false) }
        repo.openVault(keyB, false)
        val back = repo.readAllEntries().associateBy { it.id }
        assertEquals(3, back.size)
        originals.forEach { o ->
            val b = back.getValue(o.id)
            assertEquals(o.title, b.title)
            assertEquals(o.username, b.username)
            assertEquals(o.notes, b.notes)
            assertEquals(o.category, b.category)
            assertEquals(o.createdAt, b.createdAt)
            assertEquals(o.modifiedAt, b.modifiedAt)
            assertEquals(String(o.passwordSecret), String(b.passwordSecret))
        }
        assertFalse("no temporary file may remain", context.getDatabasePath("$hotName.rekey").exists())
    }

    @Test fun wrongCurrentPassphraseChangesNothing() = runBlocking {
        repo.openVault(keyA, false)
        repo.addEntry(entry("1", "One", "pw1"))

        expectFailure<WrongPassphraseException> { repo.changePassphrase(keyC, keyB, false) }

        assertTrue("vault stays open", repo.isVaultOpen())
        assertEquals(1, repo.readAllEntries().size)
        repo.closeVault()
        repo.openVault(keyA, false) // the old key still works
        assertEquals(1, repo.readAllEntries().size)
    }

    @Test fun aLeftoverTempFileDoesNotBreakAChange() = runBlocking {
        repo.openVault(keyA, false)
        repo.addEntry(entry("1", "One", "pw1"))
        context.getDatabasePath("$hotName.rekey").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(100) { 7 })
        }

        repo.changePassphrase(keyA, keyB, false)

        repo.openVault(keyB, false)
        assertEquals(1, repo.readAllEntries().size)
    }

    @Test fun passphraseCanBeChangedRepeatedly() = runBlocking {
        repo.openVault(keyA, false)
        repo.addEntry(entry("1", "One", "pw1"))
        repo.changePassphrase(keyA, keyB, false)
        repo.openVault(keyB, false)
        repo.changePassphrase(keyB, keyC, false)
        repo.openVault(keyC, false)
        assertEquals("pw1", String(repo.readAllEntries().single().passwordSecret))
    }

    @Test fun coldVaultPassphraseChangeKeepsBlankCategories() = runBlocking {
        repo.openVault(keyA, true)
        repo.addEntry(entry("1", "Cold one", "pw1"))
        repo.changePassphrase(keyA, keyB, true)
        repo.openVault(keyB, true)
        val back = repo.readAllEntries().single()
        assertEquals("Cold one", back.title)
        assertEquals("", back.category)
    }

    @Test fun changeIsRefusedWhenNothingOrTheOtherVaultIsOpen() {
        runBlocking {
            expectFailure<IllegalStateException> { repo.changePassphrase(keyA, keyB, false) }
            repo.openVault(keyA, false)
            expectFailure<IllegalStateException> { repo.changePassphrase(keyA, keyB, true) }
        }
    }
}

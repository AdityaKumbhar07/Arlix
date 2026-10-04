package com.arlix.shadowvault.data

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arlix.shadowvault.domain.VaultEntry
import com.arlix.shadowvault.testutil.VaultTestFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.activity.ComponentActivity
import org.junit.Rule

@RunWith(AndroidJUnit4::class)
class VaultRepositoryInstrumentedTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repo: VaultRepositoryImpl

    private fun key(b: Int) = ByteArray(32) { b.toByte() }
    private fun entry(i: Int, title: String = "T$i", pw: String = "pw$i", cat: String = "General") =
        VaultEntry(id = "id$i", title = title, username = "u$i", notes = "n$i", passwordSecret = pw.toCharArray(), category = cat)
    private suspend fun readAll() = repo.getAllEntries().first()

    private suspend fun assertOpenFails(k: ByteArray, cold: Boolean = false) {
        try { repo.openVault(k, cold); fail("expected the open to fail") } catch (e: Exception) { /* expected */ }
    }

    @Before fun setUp() { VaultTestFiles.wipeAll(ctx); repo = VaultRepositoryImpl(ctx) }
    @After fun tearDown() { runBlocking { repo.closeVault() }; VaultTestFiles.wipeAll(ctx) }

    @Test fun vaultExistsOnlyAfterFirstOpen() = runBlocking {
        assertFalse(repo.vaultExists(false))
        repo.openVault(key(1), false)
        assertTrue(repo.vaultExists(false)); assertFalse(repo.vaultExists(true)); assertTrue(repo.isVaultOpen())
    }

    @Test fun entryRoundTripsWithAllFields() = runBlocking {
        repo.openVault(key(1), false)
        val e = VaultEntry(id = "x", title = "Bank", username = "me@x.com", notes = "pin: none", passwordSecret = "p@ss😀".toCharArray(), category = "Finance")
        repo.addEntry(e)
        val r = readAll().single()
        assertEquals("Bank", r.title); assertEquals("me@x.com", r.username); assertEquals("pin: none", r.notes)
        assertEquals("Finance", r.category); assertEquals("p@ss😀", String(r.passwordSecret))
        assertEquals(e.createdAt, r.createdAt)
    }

    @Test fun dataSurvivesCloseAndReopenWithNewRepositoryInstance() = runBlocking {
        repo.openVault(key(2), false); repeat(100) { repo.addEntry(entry(it)) }; repo.closeVault()
        val again = VaultRepositoryImpl(ctx)
        again.openVault(key(2), false)
        assertEquals(100, again.getAllEntries().first().size)
        again.closeVault()
    }

    @Test fun wrongKeyThrowsAndVaultStaysClosed() = runBlocking {
        repo.openVault(key(3), false); repo.addEntry(entry(1)); repo.closeVault()
        assertOpenFails(key(4))
        assertFalse(repo.isVaultOpen())
    }

    @Test fun manyWrongAttemptsDoNotDamageTheVault() = runBlocking {
        repo.openVault(key(3), false); repo.addEntry(entry(1)); repo.closeVault()
        repeat(5) { assertOpenFails(key(50 + it)) }
        repo.openVault(key(3), false)
        assertEquals(1, readAll().size)
    }

    @Test fun wrongColdKeyLeavesHotVaultOpenAndReadable() = runBlocking {
        repo.openVault(key(7), true); repo.closeVault()           // create cold
        repo.openVault(key(5), false); repo.addEntry(entry(1))     // hot open with data
        assertOpenFails(key(99), cold = true)
        assertTrue(repo.isVaultOpen())
        assertEquals("T1", readAll().single().title)
    }

    @Test fun hotAndColdAreIsolatedAndSwapCleanly() = runBlocking {
        repo.openVault(key(5), false); repo.addEntry(entry(1, title = "HotOnly"))
        repo.openVault(key(7), true);  repo.addEntry(entry(2, title = "ColdOnly"))
        assertEquals("ColdOnly", readAll().single().title)
        repo.openVault(key(5), false)
        assertEquals("HotOnly", readAll().single().title)
    }

    @Test fun addingSameIdReplacesAndDeleteRemoves() = runBlocking {
        repo.openVault(key(1), false)
        repo.addEntry(entry(1, title = "Old", pw = "one")); repo.addEntry(entry(1, title = "New", pw = "two"))
        val r = readAll().single()
        assertEquals("New", r.title); assertEquals("two", String(r.passwordSecret))
        repo.deleteEntry("id1"); assertEquals(0, readAll().size)
        repo.deleteEntry("missing") // must not throw
    }

    @Test fun entriesComeBackSortedByTitle() = runBlocking {
        repo.openVault(key(1), false)
        listOf("c3", "a1", "b2").forEachIndexed { i, t -> repo.addEntry(entry(i, title = t)) }
        assertEquals(listOf("a1", "b2", "c3"), readAll().map { it.title })
    }

    @Test fun operationsOnClosedVaultThrow() {
        assertThrows(IllegalStateException::class.java) { runBlocking { repo.addEntry(entry(1)) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { repo.deleteEntry("x") } }
        assertThrows(IllegalStateException::class.java) { runBlocking { readAll() } }
    }

    @Test fun closeTwiceAndReopenSameVaultAreHarmless() = runBlocking {
        repo.openVault(key(1), false); repo.addEntry(entry(1))
        repo.openVault(key(1), false)
        assertEquals(1, readAll().size)
        repo.closeVault(); repo.closeVault()
    }

    /** The use case wipes the caller's key right after the open: later connections must not break. */
    @Test fun wipingCallersKeyAfterOpenDoesNotBreakLaterConnections() = runBlocking {
        val k = key(9)
        repo.openVault(k, false)
        k.fill(0)
        repo.addEntry(entry(1))
        val sizes = withContext(Dispatchers.IO) { (1..8).map { async { readAll().size } }.awaitAll() }
        assertTrue(sizes.all { it >= 1 })
    }

    @Test fun concurrentAddsFromManyCoroutinesAllLand() = runBlocking {
        repo.openVault(key(1), false)
        withContext(Dispatchers.IO) {
            (0 until 8).map { w -> async { repeat(50) { i -> repo.addEntry(entry(w * 1000 + i)) } } }.awaitAll()
        }
        assertEquals(400, readAll().size)
    }

    @Test fun hundredEntries() = runBlocking {
        repo.openVault(key(1), false); repeat(100) { repo.addEntry(entry(it)) }
        assertEquals(100, readAll().size)
    }

    @Test fun thousandEntriesPersistAndReload() = runBlocking {
        repo.openVault(key(1), false)
        val t0 = System.nanoTime()
        repeat(1_000) { repo.addEntry(entry(it, cat = listOf("General", "Finance", "Work")[it % 3])) }
        Log.i("ArlixTiming", "1000 inserts: ${(System.nanoTime() - t0) / 1_000_000} ms")
        repo.closeVault(); repo.openVault(key(1), false)
        val all = readAll()
        assertEquals(1_000, all.size)
        assertEquals(1_000, all.map { it.id }.toSet().size)
        assertEquals("pw999", String(all.first { it.id == "id999" }.passwordSecret))
    }

    @Test fun awkwardValuesRoundTripExactly() = runBlocking {
        repo.openVault(key(1), false)
        val weird = listOf("", " ", "a'b\"c", "'; DROP TABLE credentials;--", "日本語", "😀🔐", "مرحبا", "line1\nline2\ttab", "a\u0000b")
        weird.forEachIndexed { i, s -> repo.addEntry(VaultEntry(id = "w$i", title = "t$i", username = s, notes = s, passwordSecret = s.toCharArray())) }
        val all = readAll()
        assertEquals(weird.size, all.size)
        weird.forEachIndexed { i, s ->
            val r = all.first { it.id == "w$i" }
            assertEquals(s, r.username); assertEquals(s, r.notes); assertEquals(s, String(r.passwordSecret))
        }
    }

    @Test fun fieldsUpTo1MbAreReadable() = runBlocking {
        repo.openVault(key(1), false)
        listOf(10_000, 100_000, 500_000, 1_000_000).forEachIndexed { i, size ->
            repo.addEntry(VaultEntry(id = "big$i", title = "big$i", username = "u", notes = "x".repeat(size), passwordSecret = "p".toCharArray()))
            val all = readAll()
            assertEquals(i + 1, all.size)
            assertEquals(size, all.first { it.id == "big$i" }.notes.length)
        }
    }

    /** PROBE: logs what happens with a 3 MB note instead of asserting. If listing fails, add field length caps. */
    @Test fun probeThreeMegabyteNote() {
        runBlocking {
            repo.openVault(key(1), false)
            val result = try {
                repo.addEntry(VaultEntry(id = "huge", title = "huge", username = "u", notes = "x".repeat(3_000_000), passwordSecret = "p".toCharArray()))
                "saved; list=" + try { readAll().size.toString() } catch (e: Throwable) { "FAILED ${e.javaClass.simpleName}" }
            } catch (e: Throwable) { "save FAILED ${e.javaClass.simpleName}" }
            Log.w("ArlixProbe", "3 MB note: $result")
        }
    }

    @Test fun noPlaintextOnDiskAndNoSqliteHeader() = runBlocking {
        val marker = "ZQX-UNIQUE-MARKER-9137"
        repo.openVault(key(3), false)
        repeat(20) { i ->
            repo.addEntry(VaultEntry(title = "$marker-title-$i", username = "$marker-user-$i", notes = "$marker-notes-$i", passwordSecret = "$marker-pw-$i".toCharArray()))
        }
        repo.closeVault()
        val db = ctx.getDatabasePath("vault_primary.db")
        val files = listOf(db, java.io.File(db.path + "-wal"), java.io.File(db.path + "-shm")).filter { it.exists() }
        assertTrue(files.isNotEmpty())
        files.forEach { f -> assertFalse("plaintext found in ${f.name}", String(f.readBytes(), Charsets.ISO_8859_1).contains(marker)) }
        assertNotEquals("SQLite format 3", String(db.readBytes().copyOf(15), Charsets.ISO_8859_1))
    }

    @Test fun tamperedHeaderFailsToOpen() = runBlocking {
        repo.openVault(key(5), false); repo.addEntry(entry(1)); repo.closeVault()
        val f = ctx.getDatabasePath("vault_primary.db")
        val bytes = f.readBytes()
        for (i in 0 until 16) bytes[i] = (bytes[i].toInt() xor 0xFF).toByte()
        f.writeBytes(bytes)
        assertOpenFails(key(5))
    }

    @Test fun truncatedDatabaseFileFailsToOpen() = runBlocking {
        repo.openVault(key(5), false); repeat(30) { repo.addEntry(entry(it)) }; repo.closeVault()
        val f = ctx.getDatabasePath("vault_primary.db")
        f.writeBytes(f.readBytes().copyOf(100))
        assertOpenFails(key(5))
    }
}

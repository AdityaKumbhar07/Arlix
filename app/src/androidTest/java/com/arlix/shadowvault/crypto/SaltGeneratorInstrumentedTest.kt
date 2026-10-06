package com.arlix.shadowvault.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arlix.shadowvault.domain.VaultFileException
import com.arlix.shadowvault.testutil.VaultTestFiles
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class SaltGeneratorInstrumentedTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before fun setUp() = VaultTestFiles.wipeAll(ctx)
    @After fun tearDown() = VaultTestFiles.wipeAll(ctx)

    @Test fun createsSixteenByteSaltAndReusesIt() {
        val a = SaltGenerator.getSalt(ctx)
        assertEquals(16, a.size)
        assertArrayEquals(a, SaltGenerator.getSalt(ctx))
    }

    @Test fun hotAndColdSaltsAreSeparateFiles() {
        val hot = SaltGenerator.getSalt(ctx, false)
        val cold = SaltGenerator.getSalt(ctx, true)
        assertFalse(hot.contentEquals(cold))
        assertTrue(ctx.getDatabasePath("vault_primary.salt").exists())
        assertTrue(ctx.getDatabasePath("vault_secondary.salt").exists())
    }

    @Test fun noTempFileLeftBehind() {
        SaltGenerator.getSalt(ctx)
        assertFalse(java.io.File(ctx.getDatabasePath("vault_primary.salt").path + ".tmp").exists())
    }

    @Test fun twoHundredFreshSaltsAreAllDifferent() {
        val seen = HashSet<List<Byte>>()
        repeat(200) { VaultTestFiles.wipeAll(ctx); seen.add(SaltGenerator.getSalt(ctx).toList()) }
        assertEquals(200, seen.size)
    }

    @Test fun parallelFirstCallsAllReturnTheSameSalt() {
        val results = ConcurrentLinkedQueue<List<Byte>>()
        (1..20).map { thread { results.add(SaltGenerator.getSalt(ctx).toList()) } }.forEach { it.join() }
        assertEquals(20, results.size)
        assertEquals(1, results.toSet().size)
    }

    @Test fun badSaltWithNoDatabaseIsReplaced() {
        val f = ctx.getDatabasePath("vault_primary.salt")
        f.parentFile?.mkdirs(); f.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        assertEquals(16, SaltGenerator.getSalt(ctx).size)
        assertEquals(16, f.readBytes().size)
    }

    @Test fun badSaltWithExistingDatabaseIsRefused() {
        val db = ctx.getDatabasePath("vault_primary.db")
        db.parentFile?.mkdirs(); db.writeBytes(byteArrayOf(1))
        ctx.getDatabasePath("vault_primary.salt").writeBytes(byteArrayOf(1, 2, 3))
        assertThrows(VaultFileException::class.java) { SaltGenerator.getSalt(ctx) }
        assertEquals(3, ctx.getDatabasePath("vault_primary.salt").readBytes().size) // untouched
    }
}

package com.arlix.shadowvault

import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that SQLCipher's SQLiteDatabase class is on the test classpath and exposes
 * the openOrCreateDatabase method that SupportOpenHelperFactory relies on.
 *
 * NOTE: hasCodec() returns false in the host-JVM test environment (no .so loaded) —
 * that is expected. This test only verifies classpath presence, not native loading.
 * Native loading is verified by the instrumented SqlCipherCrashTest on a real device.
 */
class SqlCipherTest {
    @Test
    fun `SQLCipher SQLiteDatabase class is accessible and has openOrCreateDatabase method`() {
        val methods = SQLiteDatabase::class.java.methods.map { it.name }
        assertTrue(
            "SQLiteDatabase must expose openOrCreateDatabase — the core method used by SupportOpenHelperFactory",
            methods.any { it == "openOrCreateDatabase" }
        )
    }
}

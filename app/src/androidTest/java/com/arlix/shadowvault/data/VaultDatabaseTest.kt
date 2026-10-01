package com.arlix.shadowvault.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultDatabaseTest {
    private lateinit var db: VaultDatabase
    private lateinit var dao: VaultDao

    @Before
    fun createDb() {
        System.loadLibrary("sqlcipher")
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        // Passphrase for SQLCipher
        val passphrase = "test_passphrase".toByteArray(Charsets.UTF_8)
        val factory = SupportOpenHelperFactory(passphrase)

        db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .openHelperFactory(factory)
            .allowMainThreadQueries()
            .build()
        dao = db.vaultDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun writeAndReadVaultEntry() = runBlocking {
        val entry = VaultEntity(
            id = "1",
            title = "Test Service",
            username = "test_user",
            notes = "test notes",
            passwordBytes = byteArrayOf(1, 2, 3),
            category = "General",
            createdAt = 12345L,
            modifiedAt = 12345L
        )
        dao.insertCredential(entry)

        val entries = dao.getAllCredentials().first()
        assertEquals(1, entries.size)
        assertEquals("Test Service", entries[0].title)
        assertEquals("test_user", entries[0].username)
        assertEquals(byteArrayOf(1, 2, 3).toList(), entries[0].passwordBytes.toList())
    }
}

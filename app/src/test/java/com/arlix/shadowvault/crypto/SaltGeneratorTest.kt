package com.arlix.shadowvault.crypto

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SaltGeneratorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `test getSalt creates new salt if file does not exist`() {
        val context = mockk<Context>()
        val saltFile = tempFolder.newFile("vault_primary.salt")
        val dbFile = tempFolder.newFile("vault_primary.db")
        saltFile.delete() // Ensure it doesn't exist
        dbFile.delete()
        
        every { context.getDatabasePath("vault_primary.salt") } returns saltFile
        every { context.getDatabasePath("vault_primary.db") } returns dbFile

        val salt = SaltGenerator.getSalt(context, isColdVault = false)
        assertEquals(16, salt.size)
        assertTrue(saltFile.exists())
        assertArrayEquals(salt, saltFile.readBytes())
    }

    @Test
    fun `test getSalt reads existing salt if file exists`() {
        val context = mockk<Context>()
        val saltFile = tempFolder.newFile("vault_primary.salt")
        val dbFile = tempFolder.newFile("vault_primary.db")
        val existingSalt = ByteArray(16) { it.toByte() }
        saltFile.writeBytes(existingSalt)

        every { context.getDatabasePath("vault_primary.salt") } returns saltFile
        every { context.getDatabasePath("vault_primary.db") } returns dbFile

        val salt = SaltGenerator.getSalt(context, isColdVault = false)
        assertArrayEquals(existingSalt, salt)
    }
}

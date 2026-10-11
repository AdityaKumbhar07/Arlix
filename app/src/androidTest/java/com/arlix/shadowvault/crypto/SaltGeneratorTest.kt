package com.arlix.shadowvault.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arlix.shadowvault.domain.VaultFileException
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SaltGeneratorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val stamp = System.nanoTime()
    private val saltName = "test_$stamp.salt" // never the real vault file names
    private val dbName = "test_$stamp.db"
    private val saltFile get() = context.getDatabasePath(saltName)
    private val dbFile get() = context.getDatabasePath(dbName)

    @After fun cleanUp() {
        saltFile.delete()
        dbFile.delete()
        File(saltFile.parentFile, "$saltName.tmp").delete()
    }

    private fun pretendTheVaultExists() {
        dbFile.parentFile?.mkdirs()
        dbFile.writeBytes(byteArrayOf(1))
    }

    private inline fun <reified T : Throwable> expectFailure(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return
            throw e
        }
        fail("Expected ${T::class.java.simpleName}")
    }

    @Test fun saltIsCreatedOnceAndThenReused() {
        val first = SaltGenerator.getSalt(context, saltName, dbName)
        val second = SaltGenerator.getSalt(context, saltName, dbName)
        assertEquals(16, first.size)
        assertArrayEquals(first, second)
        assertFalse("no temp file may remain", File(saltFile.parentFile, "$saltName.tmp").exists())
    }

    @Test fun missingSaltNextToAnExistingVaultIsAnErrorNotANewSalt() {
        pretendTheVaultExists()
        expectFailure<VaultFileException> { SaltGenerator.getSalt(context, saltName, dbName) }
        assertFalse("no salt may be created", saltFile.exists())
    }

    @Test fun damagedSaltNextToAnExistingVaultIsAnError() {
        pretendTheVaultExists()
        saltFile.writeBytes(ByteArray(5))
        expectFailure<VaultFileException> { SaltGenerator.getSalt(context, saltName, dbName) }
        assertEquals("the damaged file must be left alone", 5L, saltFile.length())
    }

    @Test fun damagedSaltWithoutAVaultIsReplaced() {
        saltFile.parentFile?.mkdirs()
        saltFile.writeBytes(ByteArray(5))
        assertEquals(16, SaltGenerator.getSalt(context, saltName, dbName).size)
        assertEquals(16L, saltFile.length())
    }
}

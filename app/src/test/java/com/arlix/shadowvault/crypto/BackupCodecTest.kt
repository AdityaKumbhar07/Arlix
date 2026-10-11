package com.arlix.shadowvault.crypto

import com.arlix.shadowvault.domain.VaultEntry
import com.arlix.shadowvault.testing.FastCrypto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class BackupCodecTest {

    private val codec = BackupCodec(FastCrypto())

    private fun pass() = "backup-pass".toCharArray()

    private fun entry(
        id: String = "id-1", title: String = "Title", username: String = "user",
        notes: String = "notes", password: String = "secret", category: String = "General",
        created: Long = 1L, modified: Long = 2L
    ) = VaultEntry(
        id = id, title = title, username = username, notes = notes,
        passwordSecret = password.toCharArray(), category = category,
        createdAt = created, modifiedAt = modified
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

    @Test fun roundTripKeepsEveryField() = runTest {
        val source = listOf(
            entry("a", "Zoom", "ana", "note", "pässword🙂", "Finance", 5L, 9L),
            entry("b", "amazon", "bob", "n".repeat(4000), "x".repeat(256), "", 7L, 8L)
        )
        val back = codec.decrypt(codec.encrypt(source, pass(), false), pass(), false)
        assertEquals(2, back.size)
        source.zip(back).forEach { (a, b) ->
            assertEquals(a.id, b.id)
            assertEquals(a.title, b.title)
            assertEquals(a.username, b.username)
            assertEquals(a.notes, b.notes)
            assertEquals(a.category, b.category)
            assertEquals(a.createdAt, b.createdAt)
            assertEquals(a.modifiedAt, b.modifiedAt)
            assertEquals(String(a.passwordSecret), String(b.passwordSecret))
        }
    }

    @Test fun emptyVaultRoundTrips() = runTest {
        val back = codec.decrypt(codec.encrypt(emptyList(), pass(), true), pass(), true)
        assertTrue(back.isEmpty())
    }

    @Test fun twoBackupsOfTheSameDataDiffer() = runTest {
        val a = codec.encrypt(listOf(entry()), pass(), false)
        val b = codec.encrypt(listOf(entry()), pass(), false)
        assertFalse(a.contentEquals(b)) // fresh salt and nonce every time
    }

    @Test fun wrongPassphraseIsRejected() = runTest {
        val bytes = codec.encrypt(listOf(entry()), pass(), false)
        expectFailure<BackupPassphraseException> {
            codec.decrypt(bytes, "not-the-pass".toCharArray(), false)
        }
    }

    @Test fun backupKindIsReported() = runTest {
        assertEquals(false, codec.backupIsCold(codec.encrypt(emptyList(), pass(), false)))
        assertEquals(true, codec.backupIsCold(codec.encrypt(emptyList(), pass(), true)))
        assertNull(codec.backupIsCold(ByteArray(100)))
    }

    @Test fun hotBackupCannotGoIntoColdVaultAndViceVersa() = runTest {
        val hot = codec.encrypt(listOf(entry()), pass(), false)
        val cold = codec.encrypt(listOf(entry()), pass(), true)
        assertEquals(false, expectFailure<BackupKindMismatchException> {
            codec.decrypt(hot, pass(), true)
        }.backupIsCold)
        assertEquals(true, expectFailure<BackupKindMismatchException> {
            codec.decrypt(cold, pass(), false)
        }.backupIsCold)
    }

    @Test fun forgedKindByteFailsAuthentication() = runTest {
        // A hot backup relabelled "cold" passes the quick kind check, but the label is part
        // of the authenticated header, so decryption must still fail.
        val hot = codec.encrypt(listOf(entry()), pass(), false)
        val forged = hot.copyOf().also { it[7] = 2 }
        expectFailure<BackupPassphraseException> { codec.decrypt(forged, pass(), true) }
    }

    @Test fun anySingleByteChangeIsRejected() = runTest {
        val bytes = codec.encrypt(listOf(entry()), pass(), false)
        for (i in bytes.indices) {
            val damaged = bytes.copyOf().also { it[i] = (it[i].toInt() xor 0x01).toByte() }
            try {
                codec.decrypt(damaged, pass(), false)
                fail("Byte $i was changed but decryption succeeded")
            } catch (e: BackupFormatException) {
            } catch (e: BackupPassphraseException) {
            } catch (e: BackupKindMismatchException) {
            }
        }
    }

    @Test fun truncatedFilesAreRejected() = runTest {
        val bytes = codec.encrypt(listOf(entry()), pass(), false)
        for (length in listOf(0, 5, 10, 35, 36, 50, bytes.size - 1)) {
            try {
                codec.decrypt(bytes.copyOf(length), pass(), false)
                fail("Truncated to $length bytes but decryption succeeded")
            } catch (e: BackupFormatException) {
            } catch (e: BackupPassphraseException) {
            }
        }
    }

    @Test fun nonBackupFilesAreRecognised() {
        assertFalse(codec.looksLikeBackup(ByteArray(0)))
        assertFalse(codec.looksLikeBackup("just a text file, not a backup at all......".toByteArray()))
    }

    @Test fun legacyVersion1FileStillDecrypts() = runTest {
        // Hand-built version 1 file (no vault-kind byte) containing zero entries.
        val salt = ByteArray(16) { 3 }
        val nonce = ByteArray(12) { 4 }
        val header = "ARLXBK".toByteArray(Charsets.US_ASCII) + byteArrayOf(1) + salt + nonce
        val key = FastCrypto().deriveMasterKey(pass(), salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(header)
        val file = header + cipher.doFinal(ByteBuffer.allocate(4).putInt(0).array())

        assertTrue(codec.looksLikeBackup(file))
        assertNull(codec.backupIsCold(file))
        assertEquals(0, codec.decrypt(file, pass(), true).size)
        assertEquals(0, codec.decrypt(file, pass(), false).size)
    }
}

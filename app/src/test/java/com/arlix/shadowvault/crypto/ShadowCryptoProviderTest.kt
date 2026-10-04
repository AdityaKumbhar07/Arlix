package com.arlix.shadowvault.crypto

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Uses the real Argon2id (BouncyCastle). Each derivation takes a fraction of a second. */
class ShadowCryptoProviderTest {

    private val provider = ShadowCryptoProvider()
    private val salt = ByteArray(16) { it.toByte() }

    private fun derive(pw: String, s: ByteArray = salt) =
        runBlocking { provider.deriveMasterKey(pw.toCharArray(), s) }

    @Test fun keyIs32Bytes() = assertEquals(32, derive("correct horse").size)

    @Test fun sameInputsGiveSameKey() = assertArrayEquals(derive("pw-12345"), derive("pw-12345"))

    @Test fun differentPasswordGivesDifferentKey() =
        assertFalse(derive("pw-12345").contentEquals(derive("pw-12346")))

    @Test fun differentSaltGivesDifferentKey() =
        assertFalse(derive("pw-12345").contentEquals(derive("pw-12345", ByteArray(16) { 9 })))

    @Test fun passwordArrayIsNotModifiedByDerivation() {
        val pw = "keep-me-12345".toCharArray()
        runBlocking { provider.deriveMasterKey(pw, salt) }
        assertArrayEquals("keep-me-12345".toCharArray(), pw)
    }

    @Test fun oneCharacterAndVeryLongPasswordsWork() {
        assertEquals(32, derive("x").size)
        assertEquals(32, derive("long".repeat(10_000)).size)
    }

    @Test fun emojiAndNonLatinPasswordsWork() {
        assertEquals(32, derive("😀😀😀😀😀").size)
        assertEquals(32, derive("密码密码密码").size)
    }

    /** Documents CURRENT behaviour: no Unicode normalisation, so these two differ. */
    @Test fun composedAndDecomposedAccentGiveDifferentKeys() {
        assertFalse(derive("caf\u00E9-pass").contentEquals(derive("cafe\u0301-pass")))
    }

    @Test fun parallelDerivationsAreIndependentAndCorrect() = runBlocking {
        val a = async { provider.deriveMasterKey("alpha-pass".toCharArray(), salt) }
        val b = async { provider.deriveMasterKey("beta-pass".toCharArray(), salt) }
        val ka = a.await(); val kb = b.await()
        assertArrayEquals(derive("alpha-pass"), ka)
        assertArrayEquals(derive("beta-pass"), kb)
    }

    @Test fun wipeZeroesBuffer() {
        val buf = ByteArray(64) { 0x7F }
        provider.wipe(buf)
        assertTrue(buf.all { it == 0.toByte() })
        provider.wipe(ByteArray(0)) // must not throw
    }

    @Test fun derivationFinishesInReasonableTime() {
        val t0 = System.nanoTime()
        derive("timing-check")
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("Argon2id (64 MiB, t=3, p=4) took $ms ms on this JVM")
        assertTrue("took $ms ms", ms < 20_000)
    }
}

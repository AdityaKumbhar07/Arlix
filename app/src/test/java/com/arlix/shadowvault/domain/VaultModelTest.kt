package com.arlix.shadowvault.domain

import com.arlix.shadowvault.data.VaultEntity
import org.junit.Assert.*
import org.junit.Test

class VaultEntryTest {
    private fun entry(id: String = "a", pw: String = "secret") = VaultEntry(
        id = id, title = "t", username = "u", notes = "n", passwordSecret = pw.toCharArray()
    )

    @Test fun annihilateZeroesPassword() {
        val e = entry()
        e.annihilate()
        assertTrue(e.passwordSecret.all { it == '\u0000' })
        assertEquals(6, e.passwordSecret.size)
    }

    @Test fun annihilateTwiceIsHarmless() { val e = entry(); e.annihilate(); e.annihilate() }

    @Test fun equalityAndHashCodeUseIdOnly() {
        assertEquals(entry("x", "one"), entry("x", "two"))
        assertEquals(entry("x", "one").hashCode(), entry("x", "two").hashCode())
        assertNotEquals(entry("x"), entry("y"))
    }

    @Test fun defaultsGenerateUniqueIdsAndCategory() {
        val ids = (1..500).map { entry().let { VaultEntry(title = "t", username = "u", notes = "n", passwordSecret = CharArray(1)).id } }
        assertEquals(500, ids.toSet().size)
        assertEquals("General", VaultEntry(title = "t", username = "u", notes = "n", passwordSecret = CharArray(1)).category)
    }

    /** Documents a trap: copy() shares the password array, so wiping a copy wipes the original. */
    @Test fun copySharesPasswordArray() {
        val a = entry()
        val b = a.copy(title = "other")
        b.annihilate()
        assertTrue(a.passwordSecret.all { it == '\u0000' })
    }
}

class VaultEntityTest {
    private fun entity(bytes: ByteArray) = VaultEntity("id", "t", "u", "n", "General", bytes, 1L, 2L)

    @Test fun roundTripsPlainAndUnicodePasswords() {
        listOf("simple", "pässwörd", "😀🔐", "密码", "a b  c ", "x".repeat(5_000)).forEach { s ->
            val d = entity(s.toByteArray(Charsets.UTF_8)).toDomain()
            assertEquals(s, String(d.passwordSecret))
        }
    }

    @Test fun emptyPasswordGivesEmptyArray() = assertEquals(0, entity(ByteArray(0)).toDomain().passwordSecret.size)

    @Test fun invalidUtf8DoesNotThrow() {
        val d = entity(byteArrayOf(0xC3.toByte(), 0x28)).toDomain()
        assertEquals("\uFFFD(", String(d.passwordSecret))
    }

    @Test fun conversionDoesNotModifyStoredBytes() {
        val bytes = "keep".toByteArray()
        entity(bytes).toDomain()
        assertArrayEquals("keep".toByteArray(), bytes)
    }

    @Test fun otherFieldsAreCopiedAndEqualityUsesId() {
        val d = entity("p".toByteArray()).toDomain()
        assertEquals("id", d.id); assertEquals("t", d.title); assertEquals("u", d.username)
        assertEquals("n", d.notes); assertEquals("General", d.category)
        assertEquals(1L, d.createdAt); assertEquals(2L, d.modifiedAt)
        assertEquals(entity(ByteArray(1)), entity(ByteArray(9)))
    }

    @Test fun veryLargePasswordRoundTrips() {
        val s = "ä😀".repeat(100_000)
        assertEquals(s, String(entity(s.toByteArray()).toDomain().passwordSecret))
    }
}

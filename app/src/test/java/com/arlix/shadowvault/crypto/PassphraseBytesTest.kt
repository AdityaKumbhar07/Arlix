package com.arlix.shadowvault.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassphraseBytesTest {

    @Test fun asciiRoundTrip() {
        val chars = "hello123".toCharArray()
        assertArrayEquals(chars, utf8BytesToChars(charArrayToUtf8Bytes(chars)))
    }

    @Test fun unicodeRoundTrip() {
        val chars = "pässwörd🙂पासवर्ड".toCharArray()
        assertArrayEquals(chars, utf8BytesToChars(charArrayToUtf8Bytes(chars)))
    }

    @Test fun bytesAreStandardUtf8() {
        val text = "aé🙂"
        assertArrayEquals(text.toByteArray(Charsets.UTF_8), charArrayToUtf8Bytes(text.toCharArray()))
    }

    @Test fun loneSurrogateIsReplacedTheSameWayEveryTime() {
        val chars = charArrayOf('a', '\uD800', 'b')
        assertArrayEquals(charArrayToUtf8Bytes(chars), charArrayToUtf8Bytes(chars))
        assertTrue(charArrayToUtf8Bytes(chars).size >= 3)
    }

    @Test fun invalidUtf8DoesNotThrow() {
        val chars = utf8BytesToChars(byteArrayOf(0x61, 0xFF.toByte(), 0x62))
        assertEquals('a', chars.first())
        assertEquals('b', chars.last())
    }

    @Test fun emptyInputsWork() {
        assertEquals(0, charArrayToUtf8Bytes(CharArray(0)).size)
        assertEquals(0, utf8BytesToChars(ByteArray(0)).size)
    }

    @Test fun constantTimeEqualsComparesCorrectly() {
        assertTrue(constantTimeEquals("abc123".toCharArray(), "abc123".toCharArray()))
        assertFalse(constantTimeEquals("abc123".toCharArray(), "abc124".toCharArray()))
        assertFalse(constantTimeEquals("abc".toCharArray(), "abcd".toCharArray()))
    }
}

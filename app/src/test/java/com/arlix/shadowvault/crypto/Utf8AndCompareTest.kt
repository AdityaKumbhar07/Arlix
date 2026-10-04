package com.arlix.shadowvault.crypto

import org.junit.Assert.*
import org.junit.Test

class Utf8ConversionTest {

    @Test fun asciiMatchesStandardEncoding() =
        assertArrayEquals("hello".toByteArray(), charArrayToUtf8Bytes("hello".toCharArray()))

    @Test fun emptyArrayGivesEmptyBytes() = assertEquals(0, charArrayToUtf8Bytes(CharArray(0)).size)

    @Test fun multiByteCharactersMatchStandardEncoding() {
        listOf("é", "€", "😀", "日本語", "مرحبا", "🇮🇳", "e\u0301", "a\u0000b").forEach { s ->
            assertArrayEquals(s, s.toByteArray(Charsets.UTF_8), charArrayToUtf8Bytes(s.toCharArray()))
        }
    }

    @Test fun loneHighSurrogateIsReplacedNotThrown() {
        val bytes = charArrayToUtf8Bytes(charArrayOf('a', '\uD83D', 'b'))
        assertEquals(3, bytes.size)
        assertEquals('?'.code.toByte(), bytes[1])
    }

    @Test fun loneLowSurrogateAndReversedPairDoNotThrow() {
        assertEquals(3, charArrayToUtf8Bytes(charArrayOf('a', '\uDE00', 'b')).size)
        assertEquals(2, charArrayToUtf8Bytes(charArrayOf('\uDE00', '\uD83D')).size)
    }

    @Test fun sameInputAlwaysGivesSameBytes() {
        val c = "pässwörd😀".toCharArray()
        assertArrayEquals(charArrayToUtf8Bytes(c), charArrayToUtf8Bytes(c))
    }

    @Test fun inputIsNotModified() {
        val c = "keep me 😀".toCharArray()
        val copy = c.copyOf()
        charArrayToUtf8Bytes(c)
        assertArrayEquals(copy, c)
    }

    @Test fun hugeInputHasExactLength() {
        val s = "a€😀".repeat(100_000)
        assertEquals(s.toByteArray().size, charArrayToUtf8Bytes(s.toCharArray()).size)
    }
}

class ConstantTimeEqualsTest {
    private fun eq(a: String, b: String) = constantTimeEquals(a.toCharArray(), b.toCharArray())

    @Test fun equalStringsMatch() { assertTrue(eq("hunter2", "hunter2")); assertTrue(eq("", "")) }
    @Test fun differentStringsDoNot() {
        assertFalse(eq("hunter2", "hunter3"))
        assertFalse(eq("Hunter2", "hunter2"))
        assertFalse(eq("xunter2", "hunter2"))
    }
    @Test fun differentLengthsDoNot() { assertFalse(eq("abc", "abcd")); assertFalse(eq("", "a")) }
    @Test fun unicodeMatches() { assertTrue(eq("pässwörd😀", "pässwörd😀")); assertFalse(eq("é", "e\u0301")) }
    @Test fun inputsAreNotModified() {
        val a = "secret".toCharArray(); val b = "secret".toCharArray()
        constantTimeEquals(a, b)
        assertArrayEquals("secret".toCharArray(), a)
        assertArrayEquals("secret".toCharArray(), b)
    }
    @Test fun loneSurrogateDoesNotThrow() {
        assertTrue(constantTimeEquals(charArrayOf('\uD83D'), charArrayOf('\uD83D')))
    }
}

package com.arlix.shadowvault.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemorySanitizerTest {

    init {
        System.loadLibrary("memory_sanitizer")
    }

    @Test
    fun testWipeNativeZerosByteArray() {
        val testData = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val expected = ByteArray(10) { 0 }
        
        MemorySanitizer.wipeNative(testData)
        
        assertArrayEquals(expected, testData)
    }
}

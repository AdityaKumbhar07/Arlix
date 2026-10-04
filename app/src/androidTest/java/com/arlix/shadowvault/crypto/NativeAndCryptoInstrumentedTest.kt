package com.arlix.shadowvault.crypto

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class NativeAndCryptoInstrumentedTest {

    @Test fun nativeWipeZeroesArraysOfManySizes() {
        listOf(1, 2, 15, 16, 17, 4_096, 1_000_000, 8_000_000).forEach { n ->
            val a = ByteArray(n) { 0xFF.toByte() }
            MemorySanitizer.wipeNative(a)
            assertTrue("size $n not fully zeroed", a.all { it == 0.toByte() })
        }
    }

    @Test fun nativeWipeOnEmptyArrayDoesNotCrash() = MemorySanitizer.wipeNative(ByteArray(0))

    @Test fun nativeWipeIsSafeFromManyThreads() {
        val arrays = (1..8).map { ByteArray(100_000) { 0x55 } }
        arrays.map { a -> thread { repeat(50) { MemorySanitizer.wipeNative(a); a.fill(0x55) }; MemorySanitizer.wipeNative(a) } }
            .forEach { it.join() }
        assertTrue(arrays.all { a -> a.all { it == 0.toByte() } })
    }

    @Test fun providerWipeUsesNativePathAndZeroes() {
        val b = ByteArray(32) { 1 }
        ShadowCryptoProvider().wipe(b)
        assertTrue(b.all { it == 0.toByte() })
    }

    @Test fun argon2TimeOnThisDevice() = runBlocking {
        val t0 = System.nanoTime()
        val key = ShadowCryptoProvider().deriveMasterKey("device-timing".toCharArray(), ByteArray(16) { 3 })
        val ms = (System.nanoTime() - t0) / 1_000_000
        Log.i("ArlixTiming", "Argon2id on this device: $ms ms")
        assertEquals(32, key.size)
        assertTrue("Argon2id took $ms ms", ms < 15_000)
    }
}

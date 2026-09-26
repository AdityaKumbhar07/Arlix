package com.arlix.shadowvault.crypto

/**
 * The JNI Bridge connecting our Kotlin domain to the C++ memory annihilator.
 *
 * SECURITY CONTRACT:
 * Every ByteArray or CharArray that ever held key material MUST pass through
 * wipeNative() before it becomes unreachable. Do not rely on the JVM GC to clear it.
 */
object MemorySanitizer {

    init {
        try {
            // Loads the C++ shared library (memory_sanitizer.so) at class-load time
            System.loadLibrary("memory_sanitizer")
        } catch (e: UnsatisfiedLinkError) {
            // Expected during host-JVM unit tests where no .so is available.
            // wipeNative() falls back to buffer.fill(0) in that case (see ShadowCryptoProvider.wipe).
        }
    }

    /**
     * Calls the C++ volatile-write loop to destroy the byte array in physical RAM,
     * bypassing the compiler's dead-store-elimination optimisation.
     */
    external fun wipeNative(array: ByteArray)

    /**
     * Wipes a CharArray without ever allocating a String on the JVM heap.
     *
     * [T11] See ShadowCryptoProvider.kt for the NIO zero-allocation rationale.
     */
    fun wipeNative(array: CharArray) {
        val bytes = charArrayToUtf8Bytes(array)
        try {
            wipeNative(bytes)
        } catch (e: UnsatisfiedLinkError) {
            bytes.fill(0)
        } finally {
            array.fill('\u0000')
        }
    }
}

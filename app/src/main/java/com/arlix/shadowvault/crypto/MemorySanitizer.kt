package com.arlix.shadowvault.crypto

/**
 * JNI bridge to a small native routine that overwrites a ByteArray with zeros using
 * volatile writes, so the compiler cannot drop the writes as dead stores.
 *
 * Honest limits: this only clears the array's CURRENT location. Android's garbage collector
 * may have copied the array earlier, and those old copies are not reachable from here.
 */
object MemorySanitizer {

    init {
        try {
            System.loadLibrary("memory_sanitizer")
        } catch (e: UnsatisfiedLinkError) {
            // Host-JVM unit tests: no .so available. ShadowCryptoProvider.wipe() falls back to fill(0).
        }
    }

    external fun wipeNative(array: ByteArray)
}

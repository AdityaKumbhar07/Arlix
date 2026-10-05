package com.arlix.shadowvault.crypto

/**
 * JNI bridge to a small native routine that overwrites a ByteArray with zeros using
 * volatile writes, so the compiler cannot drop the writes as dead stores.
 *
 * Best effort only: this clears the array's CURRENT location. ART's garbage collector may
 * have copied the array earlier, and those old copies are not reachable from here.
 */
object MemorySanitizer {

    private val nativeAvailable: Boolean = try {
        System.loadLibrary("memory_sanitizer")
        true
    } catch (e: UnsatisfiedLinkError) {
        // Host-JVM unit tests have no .so; wipe() falls back to fill(0).
        false
    }

    external fun wipeNative(array: ByteArray)

    /** The single entry point for zeroing key material. Never throws. */
    fun wipe(array: ByteArray) {
        if (nativeAvailable) {
            try {
                wipeNative(array)
                return
            } catch (e: UnsatisfiedLinkError) {
                // fall through to the Kotlin wipe
            }
        }
        array.fill(0)
    }
}

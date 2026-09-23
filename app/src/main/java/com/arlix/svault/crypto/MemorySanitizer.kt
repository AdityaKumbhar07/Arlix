package com.arlix.svault.crypto

/**
 * The JNI Bridge connecting our Kotlin domain to the C++ memory annihilator.
 */
object MemorySanitizer {

    init {
        // Loads the C++ library when the app starts
        System.loadLibrary("memory_sanitizer")
    }

    /**
     * Calls the C++ explicit_bzero function to destroy the byte array in physical RAM.
     */
    external fun wipeNative(array: ByteArray)

    /**
     * Helper for CharArrays (converts to bytes, wipes native, then clears chars).
     */
    fun wipeNative(array: CharArray) {
        val bytes = String(array).toByteArray()
        wipeNative(bytes)
        array.fill('\u0000')
    }
}

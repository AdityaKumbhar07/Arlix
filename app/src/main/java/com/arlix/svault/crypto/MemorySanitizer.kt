package com.arlix.svault.crypto

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
            android.util.Log.d("SHADOW_VAULT_JNI", "System.loadLibrary completed without error")
        } catch (e: UnsatisfiedLinkError) {
            // Expected during host-JVM unit tests where no .so is available.
            // wipeNative() falls back to buffer.fill(0) in that case (see ShadowCryptoProvider.wipe).
            android.util.Log.e("SHADOW_VAULT_JNI", "FAILED to load memory_sanitizer.so", e)
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
     * WHY NO String():
     * String(array) would allocate an immutable copy on the heap that we can never zero —
     * the exact "JVM String Trap" (T11) this whole module is designed to prevent.
     *
     * NIO PATH (zero String allocation):
     * 1. charArrayToUtf8Bytes(array) wraps the CharBuffer in-place, encodes to UTF-8 bytes.
     * 2. We wipe the resulting byte representation natively.
     * 3. We zero the original CharArray with fill('\u0000').
     */
    fun wipeNative(array: CharArray) {
        val bytes = charArrayToUtf8Bytes(array)   // NIO — no String object on heap
        try {
            wipeNative(bytes)
            android.util.Log.d("SHADOW_VAULT_JNI", "wipeNative call returned without UnsatisfiedLinkError")
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e("SHADOW_VAULT_JNI", "UnsatisfiedLinkError: Native wipe FAILED! Falling back to fill(0).", e)
            bytes.fill(0)
        } finally {
            array.fill('\u0000')
        }
    }
}

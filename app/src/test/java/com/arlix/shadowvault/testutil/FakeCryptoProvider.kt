package com.arlix.shadowvault.testutil

import com.arlix.shadowvault.domain.ICryptoProvider
import kotlinx.coroutines.CompletableDeferred
import java.security.MessageDigest

/** Fast deterministic stand-in for Argon2id: same password + salt gives the same key. */
class FakeCryptoProvider : ICryptoProvider {
    /** If set, deriveMasterKey waits for this (lets tests hold an unlock "in progress"). */
    var gate: CompletableDeferred<Unit>? = null
    /** If set, deriveMasterKey throws this. */
    var failWith: Throwable? = null
    var deriveCalls = 0
        private set
    val wipedBuffers = mutableListOf<ByteArray>()

    override suspend fun deriveMasterKey(password: CharArray, salt: ByteArray): ByteArray {
        deriveCalls++
        gate?.await()
        failWith?.let { throw it }
        val md = MessageDigest.getInstance("SHA-256")
        md.update(String(password).toByteArray(Charsets.UTF_8))
        md.update(salt)
        return md.digest()
    }

    override fun wipe(buffer: ByteArray) {
        buffer.fill(0)
        wipedBuffers += buffer
    }
}

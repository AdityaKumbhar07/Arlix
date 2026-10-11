package com.arlix.shadowvault.testing

import com.arlix.shadowvault.domain.ICryptoProvider
import com.arlix.shadowvault.domain.IVaultRepository
import com.arlix.shadowvault.domain.VaultEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.security.MessageDigest

/** Fast stand-in for Argon2: deterministic and salt-dependent, but cheap. */
class FastCrypto : ICryptoProvider {
    /** A test can close this gate to hold a key derivation in progress. */
    @Volatile
    var gate: CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { it.complete(Unit) }

    /** The array handed out by the last derivation, so tests can check it was wiped. */
    @Volatile
    var lastKey: ByteArray? = null

    override suspend fun deriveMasterKey(password: CharArray, salt: ByteArray): ByteArray {
        gate.await()
        val md = MessageDigest.getInstance("SHA-256")
        md.update(String(password).toByteArray(Charsets.UTF_8))
        md.update(salt)
        return md.digest().also { lastKey = it }
    }

    override fun wipe(buffer: ByteArray) {
        buffer.fill(0)
    }
}

/** In-memory repository whose failures can be switched on by a test. */
class FakeVaultRepository : IVaultRepository {
    var hotExists = true
    var coldExists = true
    val openCalls = mutableListOf<Boolean>() // isColdVault of each open attempt
    var closeCalls = 0
    var failOpenWith: Throwable? = null
    var failAdd = false
    var failFlowWith: Exception? = null
    var openGate: CompletableDeferred<Unit>? = null

    private var open = false
    private val entries = MutableStateFlow<List<VaultEntry>>(emptyList())

    /** Puts an entry into the "database" without any suspension. */
    fun seed(entry: VaultEntry) {
        entries.value = (entries.value.filterNot { it.id == entry.id } + entry).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    override suspend fun openVault(masterKey: ByteArray, isColdVault: Boolean): Boolean {
        openCalls += isColdVault
        failOpenWith?.let { throw it }
        open = true
        openGate?.await() // lets a test cancel "in the middle of opening"
        return true
    }

    override suspend fun closeVault() {
        closeCalls++
        open = false
    }

    override fun isVaultOpen(): Boolean = open

    override fun vaultExists(isColdVault: Boolean): Boolean = if (isColdVault) coldExists else hotExists

    override fun getAllEntries(): Flow<List<VaultEntry>> = flow {
        failFlowWith?.let { throw it }
        emitAll(entries)
    }

    override suspend fun addEntry(entry: VaultEntry) {
        if (failAdd) throw IllegalStateException("disk full")
        entries.value = (entries.value.filterNot { it.id == entry.id } + entry).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    override suspend fun deleteEntry(entryId: String) {
        entries.value = entries.value.filterNot { it.id == entryId }
    }

    override suspend fun readAllEntries(): List<VaultEntry> = entries.value

    override suspend fun addEntries(entries: List<VaultEntry>) {
        var list = this.entries.value
        for (e in entries) {
            list = list.filterNot { it.id == e.id } + e
        }
        this.entries.value = list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    override suspend fun changePassphrase(oldKey: ByteArray, newKey: ByteArray, isColdVault: Boolean) {
        throw UnsupportedOperationException("covered by the instrumented tests")
    }
}

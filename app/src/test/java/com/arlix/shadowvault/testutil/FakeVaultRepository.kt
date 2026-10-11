package com.arlix.shadowvault.testutil

import com.arlix.shadowvault.domain.IVaultRepository
import com.arlix.shadowvault.domain.VaultEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Copies share nothing with the original: the ViewModel wipes what it receives. */
fun VaultEntry.deepCopy(): VaultEntry = copy(passwordSecret = passwordSecret.copyOf())

/**
 * In-memory stand-in for the SQLCipher repository. Mirrors the real rules:
 * a vault is created on first open; a wrong key throws and leaves the currently open
 * vault untouched; adding an existing id replaces it; entries are sorted by title.
 */
class FakeVaultRepository : IVaultRepository {

    private class Vault(val key: ByteArray) {
        val entries = kotlinx.coroutines.flow.MutableSharedFlow<List<VaultEntry>>(
            replay = 1,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
        ).apply { tryEmit(emptyList()) }
        
        var currentEntries: List<VaultEntry> = emptyList()
            set(value) {
                field = value
                entries.tryEmit(value)
            }
    }

    private val vaults = HashMap<Boolean, Vault>()
    private var current: Vault? = null

    var openIsCold: Boolean? = null
        private set
    var openCalls = 0
        private set
    var closeCalls = 0
        private set
    var failNextAdd: Throwable? = null
    var failNextDelete: Throwable? = null
    var failReads = false

    fun seedVault(isCold: Boolean, key: ByteArray, entries: List<VaultEntry> = emptyList()) {
        val v = Vault(key.copyOf())
        v.currentEntries = entries.map { it.deepCopy() }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        vaults[isCold] = v
    }

    fun storedEntries(isCold: Boolean): List<VaultEntry> = vaults[isCold]?.currentEntries ?: emptyList()
    fun storedKey(isCold: Boolean): ByteArray? = vaults[isCold]?.key?.copyOf()

    override suspend fun openVault(masterKey: ByteArray, isColdVault: Boolean): Boolean {
        openCalls++
        val existing = vaults[isColdVault]
        val vault = when {
            existing == null -> Vault(masterKey.copyOf()).also { vaults[isColdVault] = it }
            existing.key.contentEquals(masterKey) -> existing
            else -> throw IllegalStateException("wrong key") // current vault stays open
        }
        current = vault
        openIsCold = isColdVault
        return true
    }

    override suspend fun closeVault() {
        closeCalls++
        current = null
        openIsCold = null
    }

    override fun isVaultOpen(): Boolean = current != null

    override fun vaultExists(isColdVault: Boolean): Boolean = vaults.containsKey(isColdVault)

    override fun getAllEntries(): Flow<List<VaultEntry>> = flow {
        if (failReads) throw java.io.IOException("read failed")
        val v = current ?: throw IllegalStateException("Vault is locked!")
        emitAll(v.entries.map { list -> list.map { it.deepCopy() } })
    }

    override suspend fun addEntry(entry: VaultEntry) {
        failNextAdd?.let { failNextAdd = null; throw it }
        val v = current ?: throw IllegalStateException("Vault is locked!")
        v.currentEntries = (v.currentEntries.filterNot { it.id == entry.id } + entry.deepCopy())
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    override suspend fun deleteEntry(entryId: String) {
        failNextDelete?.let { failNextDelete = null; throw it }
        val v = current ?: throw IllegalStateException("Vault is locked!")
        v.currentEntries = v.currentEntries.filterNot { it.id == entryId }
    }

    override suspend fun readAllEntries(): List<VaultEntry> {
        if (failReads) throw java.io.IOException("read failed")
        val v = current ?: throw IllegalStateException("Vault is locked!")
        return v.currentEntries.map { it.deepCopy() }
    }

    override suspend fun addEntries(entries: List<VaultEntry>) {
        failNextAdd?.let { failNextAdd = null; throw it }
        val v = current ?: throw IllegalStateException("Vault is locked!")
        var list = v.currentEntries
        for (e in entries) {
            list = list.filterNot { it.id == e.id } + e.deepCopy()
        }
        v.currentEntries = list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    override suspend fun changePassphrase(oldKey: ByteArray, newKey: ByteArray, isColdVault: Boolean) {
        val v = current ?: throw java.lang.IllegalStateException("Nothing is open")
        if (openIsCold != isColdVault) throw java.lang.IllegalStateException("Another vault is open")
        if (!v.key.contentEquals(oldKey)) throw com.arlix.shadowvault.domain.WrongPassphraseException()
        
        val newVault = Vault(newKey.copyOf())
        newVault.currentEntries = v.currentEntries.map { it.deepCopy() }
        vaults[isColdVault] = newVault
        current = null
        openIsCold = null
    }
}

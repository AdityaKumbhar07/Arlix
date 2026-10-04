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
        val entries = MutableStateFlow<List<VaultEntry>>(emptyList())
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
        v.entries.value = entries.map { it.deepCopy() }.sortedBy { it.title }
        vaults[isCold] = v
    }

    fun storedEntries(isCold: Boolean): List<VaultEntry> = vaults[isCold]?.entries?.value ?: emptyList()
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
        v.entries.value = (v.entries.value.filterNot { it.id == entry.id } + entry.deepCopy())
            .sortedBy { it.title }
    }

    override suspend fun deleteEntry(entryId: String) {
        failNextDelete?.let { failNextDelete = null; throw it }
        val v = current ?: throw IllegalStateException("Vault is locked!")
        v.entries.value = v.entries.value.filterNot { it.id == entryId }
    }
}

package com.arlix.shadowvault.domain.usecase

import com.arlix.shadowvault.domain.IVaultRepository

class LockVaultUseCase(
    private val vaultRepository: IVaultRepository
) {
    private val _lockEvents = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val lockEvents: kotlinx.coroutines.flow.Flow<Unit> = _lockEvents

    suspend operator fun invoke() {
        // Closes the DB and commands SQLCipher to purge keys from its C++ memory
        vaultRepository.closeVault()
        // NOTE: manual lock() also triggers this collector via its own lockEvents.tryEmit — redundant but harmless double state-write, not a bug.
        _lockEvents.tryEmit(Unit)
    }
}

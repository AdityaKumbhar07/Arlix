package com.arlix.svault.domain.usecase

import com.arlix.svault.domain.IVaultRepository

class LockVaultUseCase(
    private val vaultRepository: IVaultRepository
) {
    private val _lockEvents = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val lockEvents: kotlinx.coroutines.flow.Flow<Unit> = _lockEvents

    suspend operator fun invoke() {
        // Closes the DB and commands SQLCipher to purge keys from its C++ memory
        vaultRepository.closeVault()
        _lockEvents.tryEmit(Unit)
    }
}

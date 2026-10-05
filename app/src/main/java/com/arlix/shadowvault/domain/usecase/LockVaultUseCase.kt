package com.arlix.shadowvault.domain.usecase

import com.arlix.shadowvault.domain.IVaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class LockVaultUseCase(
    private val vaultRepository: IVaultRepository
) {
    private val _lockEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits after every lock, so the UI can wipe its in-memory state and show the lock screen. */
    val lockEvents: Flow<Unit> = _lockEvents

    suspend operator fun invoke() {
        try {
            // Closes the database and zeroes the key.
            vaultRepository.closeVault()
        } finally {
            // Even if closing failed, the UI must still return to the lock screen.
            _lockEvents.tryEmit(Unit)
        }
    }
}

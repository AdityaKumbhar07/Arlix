package com.arlix.svault.domain.usecase

import com.arlix.svault.domain.IVaultRepository

class LockVaultUseCase(
    private val vaultRepository: IVaultRepository
) {
    suspend operator fun invoke() {
        // Closes the DB and commands SQLCipher to purge keys from its C++ memory
        vaultRepository.closeVault()
    }
}

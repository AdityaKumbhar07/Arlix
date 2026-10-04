package com.arlix.shadowvault

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arlix.shadowvault.crypto.SaltGenerator
import com.arlix.shadowvault.crypto.ShadowCryptoProvider
import com.arlix.shadowvault.data.VaultRepositoryImpl
import com.arlix.shadowvault.domain.VaultEntry
import com.arlix.shadowvault.domain.usecase.UnlockVaultUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DataGeneratorTest {

    @Test
    fun generateFiftyEntries() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val repo = VaultRepositoryImpl(ctx)
        val unlock = UnlockVaultUseCase(ShadowCryptoProvider(), repo)

        unlock("12345".toCharArray(), SaltGenerator.getSalt(ctx, false), false)

        for (i in 1..50) {
            repo.addEntry(
                VaultEntry(
                    id = UUID.randomUUID().toString(),
                    title = "Generated Hot Bank $i",
                    username = "agent_$i",
                    notes = "This is a generated test entry for manual QA.",
                    passwordSecret = "classified_secret_$i".toCharArray()
                )
            )
        }
        repo.closeVault()
    }

    @Test
    fun generateFiftyColdEntries() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val repo = VaultRepositoryImpl(ctx)
        val unlock = UnlockVaultUseCase(ShadowCryptoProvider(), repo)

        // Ensure you have created the Cold Vault with 'coldpass999' before running!
        unlock("12345".toCharArray(), SaltGenerator.getSalt(ctx, true), true)

        for (i in 1..50) {
            repo.addEntry(
                VaultEntry(
                    id = UUID.randomUUID().toString(),
                    title = "Generated Cold Wallet $i",
                    username = "ghost_$i",
                    notes = "Top secret cold storage QA entry.",
                    passwordSecret = "cold_secret_$i".toCharArray()
                )
            )
        }
        repo.closeVault()
    }
}

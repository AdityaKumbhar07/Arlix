package com.arlix.shadowvault
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.arlix.shadowvault.data.VaultRepositoryImpl
import com.arlix.shadowvault.domain.IVaultRepository
import com.arlix.shadowvault.domain.usecase.LockVaultUseCase
import com.arlix.shadowvault.domain.usecase.UnlockVaultUseCase
import com.arlix.shadowvault.crypto.ShadowCryptoProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

class ArlixApplication : Application(), DefaultLifecycleObserver {

    lateinit var cryptoProvider: ShadowCryptoProvider
        private set
    lateinit var vaultRepository: IVaultRepository
        private set
    lateinit var lockUseCase: LockVaultUseCase
        private set
    lateinit var unlockUseCase: UnlockVaultUseCase
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                val pendingResult = goAsync()
                applicationScope.launch {
                    try {
                        lockUseCase()
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super<Application>.onCreate()
        cryptoProvider = ShadowCryptoProvider()
        vaultRepository = VaultRepositoryImpl(applicationContext)
        lockUseCase = LockVaultUseCase(vaultRepository)
        unlockUseCase = UnlockVaultUseCase(cryptoProvider, vaultRepository)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        
        // Register receiver for ACTION_SCREEN_OFF at runtime (cannot be in manifest)
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onStop(owner: LifecycleOwner) {
        // Fires only when the ENTIRE app (all activities) has left the foreground —
        // not on transient dialogs, rotation, or single-activity pause/resume churn.
        runBlocking { lockUseCase() }
        // runBlocking is deliberate here: onStop has no coroutine scope guarantee to
        // survive past this callback, and the lock operation (DB close + native wipe)
        // must complete before the process is eligible for backgrounding/kill.
        // This operation is fast (DB close, no Argon2id math) — acceptable to block.
    }
}

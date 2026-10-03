package com.arlix.shadowvault
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.arlix.shadowvault.crypto.ShadowCryptoProvider
import com.arlix.shadowvault.data.VaultRepositoryImpl
import com.arlix.shadowvault.domain.IVaultRepository
import com.arlix.shadowvault.domain.usecase.LockVaultUseCase
import com.arlix.shadowvault.domain.usecase.UnlockVaultUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
        // ProcessLifecycleOwner fires ON_STOP only after ALL activities have been stopped
        // for ~700 ms, so rotation and short dialogs never reach here.
        // Deliberately NOT blocking the main thread: if the process is killed, the keys
        // disappear with it, so there is nothing that must finish first.
        applicationScope.launch { lockUseCase() }
    }
}

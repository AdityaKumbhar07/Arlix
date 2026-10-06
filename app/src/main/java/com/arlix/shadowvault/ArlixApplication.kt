package com.arlix.shadowvault

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.arlix.shadowvault.crypto.BackupCodec
import com.arlix.shadowvault.crypto.ShadowCryptoProvider
import com.arlix.shadowvault.data.VaultRepositoryImpl
import com.arlix.shadowvault.domain.IVaultRepository
import com.arlix.shadowvault.domain.usecase.ChangePassphraseUseCase
import com.arlix.shadowvault.domain.usecase.LockVaultUseCase
import com.arlix.shadowvault.domain.usecase.UnlockVaultUseCase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Owns the process-wide singletons and is the ONE place that locks the vault when the app
 * leaves the foreground or the screen turns off.
 */
class ArlixApplication : Application(), DefaultLifecycleObserver {

    lateinit var cryptoProvider: ShadowCryptoProvider
        private set
    lateinit var vaultRepository: IVaultRepository
        private set
    lateinit var lockUseCase: LockVaultUseCase
        private set
    lateinit var unlockUseCase: UnlockVaultUseCase
        private set

    lateinit var changePassphraseUseCase: ChangePassphraseUseCase
        private set

    lateinit var backupCodec: BackupCodec
        private set

    // A failure while locking must never crash the app in the background. The repository
    // already resets its state and zeroes the key in a finally block before any exception escapes.
    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> }
    )

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
        changePassphraseUseCase = ChangePassphraseUseCase(cryptoProvider, vaultRepository)
        backupCodec = BackupCodec(cryptoProvider)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // ACTION_SCREEN_OFF cannot be declared in the manifest, so it is registered at runtime.
        // It is a protected system broadcast, so no export flag is required.
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onStop(owner: LifecycleOwner) {
        // ProcessLifecycleOwner fires ON_STOP only after ALL activities have been stopped for
        // ~700 ms, so rotation and short dialogs never reach here. Not blocking the main
        // thread is deliberate: if the process is killed, the keys disappear with it.
        applicationScope.launch { lockUseCase() }
    }
}

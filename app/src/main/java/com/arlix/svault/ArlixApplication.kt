package com.arlix.svault
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.arlix.svault.data.VaultRepositoryImpl
import com.arlix.svault.domain.IVaultRepository
import com.arlix.svault.domain.usecase.LockVaultUseCase
import kotlinx.coroutines.runBlocking

class ArlixApplication : Application(), DefaultLifecycleObserver {

    lateinit var vaultRepository: IVaultRepository
        private set
    lateinit var lockUseCase: LockVaultUseCase
        private set

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                // Reuse the same runBlocking approach as onStop, same fast-operation justification.
                runBlocking { lockUseCase() }
            }
        }
    }

    override fun onCreate() {
        super<Application>.onCreate()
        vaultRepository = VaultRepositoryImpl(applicationContext)
        lockUseCase = LockVaultUseCase(vaultRepository)
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

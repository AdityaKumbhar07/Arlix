package com.arlix.shadowvault

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arlix.shadowvault.crypto.ShadowCryptoProvider
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KdfTimingTest {
    @Test fun timeKeyDerivation() = runBlocking {
        val salt = ByteArray(16)
        repeat(4) { run ->
            val start = System.nanoTime()
            ShadowCryptoProvider().deriveMasterKey("benchmark-pass".toCharArray(), salt)
            Log.i("KDF", "run $run: ${(System.nanoTime() - start) / 1_000_000} ms")
        }
    }
}

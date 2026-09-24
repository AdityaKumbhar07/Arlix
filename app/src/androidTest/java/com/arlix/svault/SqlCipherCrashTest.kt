package com.arlix.svault

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arlix.svault.data.VaultRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SqlCipherCrashTest {
    @Test
    fun testSqlCipherOpen() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = VaultRepositoryImpl(appContext)
        val dummyKey = ByteArray(32) { 0 }
        repo.openVault(dummyKey, false)
        repo.getAllEntries().collect { }
    }
}

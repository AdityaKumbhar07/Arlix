package com.arlix.shadowvault.testutil

import android.content.Context
import java.io.File

/** DANGER: deletes the app's real vault files. Run only on an emulator or a test device. */
object VaultTestFiles {
    private val names = listOf("vault_primary.db", "vault_secondary.db", "vault_primary.salt", "vault_secondary.salt")

    fun wipeAll(context: Context) {
        names.forEach { n ->
            val f = context.getDatabasePath(n)
            listOf(f, File(f.path + "-wal"), File(f.path + "-shm"), File(f.path + "-journal"), File(f.path + ".tmp"))
                .forEach { it.delete() }
        }
    }
}

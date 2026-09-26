package com.arlix.shadowvault.crypto

import android.content.Context
import java.io.File
import java.security.SecureRandom

object SaltGenerator {
    fun getSalt(context: Context, isColdVault: Boolean = false): ByteArray {
        val fileName = if (isColdVault) "vault_secondary.salt" else "vault_primary.salt"
        val file = File(context.getDatabasePath(fileName).path)

        if (file.exists()) return file.readBytes()

        val newSalt = ByteArray(16)
        SecureRandom().nextBytes(newSalt)
        file.writeBytes(newSalt)
        return newSalt
    }
}

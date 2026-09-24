package com.arlix.svault.crypto

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

object SaltGenerator {
    private const val PREF_NAME = "secure_prefs"
    private const val KEY_SALT = "vault_salt"

    // ⚠️ PORTABILITY WARNING (tracked issue, not yet fixed):
    // The salt is stored in EncryptedSharedPreferences, which is backed by Android Keystore.
    // Keystore keys are hardware-bound to THIS specific device — they cannot be exported.
    // CONSEQUENCE: If you copy the vault_primary.db file to a new device (which the project docs
    // promise is possible via Argon2id-based portability), the same passphrase on the new device
    // will derive a DIFFERENT key because the salt will be missing. The vault will be permanently
    // unreadable on the new device — silent data loss despite the passphrase being correct.
    //
    // THE FIX (future milestone): Store the salt in a DB metadata table that travels WITH the DB file,
    // rather than in device-bound EncryptedSharedPreferences. This makes portability real.
    // Until that fix ships, the portability claim in the docs is technically false.

    fun getSalt(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val sharedPreferences = EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val existingSaltBase64 = sharedPreferences.getString(KEY_SALT, null)
        if (existingSaltBase64 != null) {
            return Base64.decode(existingSaltBase64, Base64.NO_WRAP)
        }

        val newSalt = ByteArray(16)
        SecureRandom().nextBytes(newSalt)
        val newSaltBase64 = Base64.encodeToString(newSalt, Base64.NO_WRAP)

        sharedPreferences.edit()
            .putString(KEY_SALT, newSaltBase64)
            .apply()

        return newSalt
    }
}


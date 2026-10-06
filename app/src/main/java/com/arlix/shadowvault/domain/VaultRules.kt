package com.arlix.shadowvault.domain

/** Rules shared by every place that creates or changes a passphrase. */
object VaultRules {
    const val MIN_PASSPHRASE_LENGTH = 5

    /**
     * Backups sit in the cloud, so an attacker only needs your Drive account to get a
     * copy to attack offline. Their passphrase therefore needs more length than a vault
     * passphrase, which never leaves the device.
     */
    const val MIN_BACKUP_PASSPHRASE_LENGTH = 8
}

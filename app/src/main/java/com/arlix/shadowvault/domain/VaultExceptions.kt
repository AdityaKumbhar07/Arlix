package com.arlix.shadowvault.domain

/** A vault support file (e.g. the salt) is missing or damaged. Not a wrong passphrase. */
class VaultFileException(message: String) : Exception(message)

/** The passphrase typed to confirm a sensitive operation does not match the open vault. */
class WrongPassphraseException : Exception("Incorrect passphrase")

package com.arlix.shadowvault.domain

import java.util.UUID

/**
 * Core Domain Entity representing a single saved credential.
 *
 * [T11] The password MUST be a CharArray (mutable buffer) so we can surgically annihilate it later.
 * Standard Strings are immutable and cannot be safely wiped from the JVM heap.
 */
data class VaultEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val username: String,
    val notes: String,
    val passwordSecret: CharArray,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
) {
    /**
     * Instantly wipes the password from physical RAM by overwriting it with empty spaces.
     */
    fun annihilate() {
        passwordSecret.fill('\u0000')
    }

    // Auto-generated equals/hashCode required because of CharArray
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VaultEntry
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

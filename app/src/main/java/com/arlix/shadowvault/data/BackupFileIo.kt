package com.arlix.shadowvault.data

import android.content.ContentResolver
import android.net.Uri
import com.arlix.shadowvault.data.BackupFileIo.MAX_BYTES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Reads and writes backup bytes through Android's file picker (any DocumentsProvider, e.g. Google Drive). */
object BackupFileIo {

    /** A real backup is a few KB to a few MB; anything bigger is not ours. */
    const val MAX_BYTES = 16 * 1024 * 1024

    /**
     * Writes [data], then reads it back and compares. Returns true only if the stored bytes
     * are identical, so a backup is never reported as saved unless it can be read again.
     */
    suspend fun write(resolver: ContentResolver, uri: Uri, data: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // "wt" truncates; some providers only support "w", which is fine for a new empty file.
                val stream = try {
                    resolver.openOutputStream(uri, "wt")
                } catch (e: Exception) {
                    resolver.openOutputStream(uri, "w")
                }
                if (stream == null) return@withContext false
                stream.use {
                    it.write(data)
                    it.flush()
                }
                val back = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext false
                back.contentEquals(data)
            } catch (e: Exception) {
                false
            }
        }

    /** Reads the whole file, or returns null if it cannot be read or is larger than [MAX_BYTES]. */
    suspend fun read(resolver: ContentResolver, uri: Uri): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                resolver.openInputStream(uri)?.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) return@withContext null
                        out.write(buffer, 0, n)
                    }
                    out.toByteArray()
                }
            } catch (e: Exception) {
                null
            }
        }
}

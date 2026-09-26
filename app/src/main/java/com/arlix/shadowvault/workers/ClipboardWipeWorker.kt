package com.arlix.shadowvault.workers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * [T2] Overwrites clipboard with blank content 10s after copying a credential.
 * Checks clipToken to avoid destroying legitimate user clipboard history.
 */
class ClipboardWipeWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    companion object {
        const val KEY_CLIP_TOKEN = "clip_token"
    }

    override fun doWork(): ListenableWorker.Result {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val expectedToken = inputData.getString(KEY_CLIP_TOKEN)

        val currentLabel = clipboard.primaryClip?.description?.label?.toString()

        if (expectedToken == null || currentLabel != expectedToken) {
            // Clipboard was overwritten by the user or already wiped.
            return ListenableWorker.Result.success()
        }

        // [T2] Token matches — overwrite with blank content.
        val wipeClip = ClipData.newPlainText("Arlix", " ")
        clipboard.setPrimaryClip(wipeClip)

        return ListenableWorker.Result.success()
    }
}

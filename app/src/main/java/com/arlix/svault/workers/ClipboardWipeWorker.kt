package com.arlix.svault.workers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * [T2: Global Clipboard Leaks] — Overwrites the clipboard with blank content 10 seconds
 * after a credential is copied, preventing malicious apps from reading it later.
 *
 * TOKEN CHECK:
 * The worker receives a [KEY_CLIP_TOKEN] that was stored in the ClipData label at copy time.
 * Before wiping, we verify the current clipboard label still matches this token. This prevents
 * the worker from clobbering something the user copied AFTER the password (e.g., a URL they
 * copied 3 seconds later) — which would be a confusing, invisible data loss from the user's POV.
 *
 * If the token doesn't match (user already copied something else), we skip the wipe.
 * The password has already left the clipboard at that point, so this is not a security gap —
 * the user's manual copy action implicitly replaced the sensitive content.
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

        // Check if the clipboard currently holds the same password we scheduled to wipe.
        // The token is stored as the clip label (not the clip content, which is the password).
        val currentLabel = clipboard.primaryClip?.description?.label?.toString()

        if (expectedToken == null || currentLabel != expectedToken) {
            // The clipboard no longer holds our password — user replaced it or it was already wiped.
            // Do nothing: wiping would destroy unrelated content the user actually wants.
            return ListenableWorker.Result.success()
        }

        // Token matches — overwrite with blank content to destroy the password
        val wipeClip = ClipData.newPlainText("ShadowVault", " ")
        clipboard.setPrimaryClip(wipeClip)

        return ListenableWorker.Result.success()
    }
}

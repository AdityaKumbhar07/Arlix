package com.arlix.svault.workers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerParameters

class ClipboardWipeWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): ListenableWorker.Result {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        // Overwrite the clipboard with empty data to kill the password
        val clip = ClipData.newPlainText("ShadowVault", " ")
        clipboard.setPrimaryClip(clip)

        return ListenableWorker.Result.success()
    }
}

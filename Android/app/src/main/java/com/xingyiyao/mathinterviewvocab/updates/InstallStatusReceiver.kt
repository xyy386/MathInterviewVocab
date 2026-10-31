package com.xingyiyao.mathinterviewvocab.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xingyiyao.mathinterviewvocab.MathVocabApplication

/** 只接受 PackageInstaller 专用 PendingIntent，并在处理前再次核对会话。 */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val application = context.applicationContext as? MathVocabApplication ?: return
        application.services.updater.handleInstallResult(intent)
    }
}

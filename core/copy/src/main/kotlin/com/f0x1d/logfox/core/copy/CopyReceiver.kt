package com.f0x1d.logfox.core.copy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.f0x1d.logfox.core.context.notificationManagerCompat
import com.f0x1d.logfox.core.context.toast
import com.f0x1d.logfox.feature.strings.Strings
import java.io.File

class CopyReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_LOG_FILE_PATH = "log_file_path"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val logFilePath = intent.getStringExtra(EXTRA_LOG_FILE_PATH)
        val text = if (logFilePath != null) {
            try {
                File(logFilePath).readText()
            } catch (e: Exception) {
                ""
            }
        } else {
            intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        }
        context.copyText(text)
        context.toast(Strings.text_copied)

        context.notificationManagerCompat.cancel(
            intent.getStringExtra(EXTRA_PACKAGE_NAME),
            intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0),
        )
    }
}

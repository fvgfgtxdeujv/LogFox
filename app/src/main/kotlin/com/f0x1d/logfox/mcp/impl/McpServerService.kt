package com.f0x1d.logfox.mcp.impl

import android.app.Notification
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.f0x1d.logfox.core.context.OPEN_APP_INTENT_ID
import com.f0x1d.logfox.core.context.makeServicePendingIntent
import com.f0x1d.logfox.core.ui.icons.Icons
import com.f0x1d.logfox.feature.notifications.api.MCP_SERVER_CHANNEL_ID
import com.f0x1d.logfox.feature.strings.Strings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class McpServerService : LifecycleService() {

    companion object {
        const val ACTION_STOP_SERVER = "mcp.STOP_SERVER"
        const val DEFAULT_PORT = 8765
    }

    @Inject
    lateinit var mcpServerManager: McpServerManager

    override fun onCreate() {
        super.onCreate()

        startForeground(1, notification())

        lifecycleScope.launch {
            mcpServerManager.start(McpServerService.DEFAULT_PORT)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_STOP_SERVER -> stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val port = McpServerService.DEFAULT_PORT
        return NotificationCompat.Builder(this, MCP_SERVER_CHANNEL_ID)
            .setContentTitle(getString(Strings.mcp_server_notification_title))
            .setContentText(getString(Strings.mcp_server_notification_text, port))
            .setSmallIcon(Icons.ic_logfox)
            .setOngoing(true)
            .setContentIntent(
                makeServicePendingIntent<McpServerService>(OPEN_APP_INTENT_ID) {
                },
            )
            .addAction(
                Icons.ic_clear,
                getString(Strings.mcp_server_stop),
                makeServicePendingIntent<McpServerService>(OPEN_APP_INTENT_ID + 10) {
                    action = ACTION_STOP_SERVER
                },
            )
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            mcpServerManager.stop()
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}

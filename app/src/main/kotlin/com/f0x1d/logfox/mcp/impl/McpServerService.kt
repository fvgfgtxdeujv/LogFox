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
import com.f0x1d.logfox.feature.preferences.api.data.ServiceSettingsRepository
import com.f0x1d.logfox.mcp.api.McpServerManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class McpServerService : LifecycleService() {

    companion object {
        const val ACTION_STOP_SERVER = "mcp.STOP_SERVER"
        const val DEFAULT_PORT = 8765
        const val DEFAULT_HOST = "0.0.0.0"
        const val EXTRA_PORT = "mcp.port"
        const val EXTRA_HOST = "mcp.host"
        private const val TAG = "[MCP]"
    }

    @Inject
    lateinit var mcpServerManager: McpServerManager

    @Inject
    lateinit var serviceSettingsRepository: ServiceSettingsRepository

    private var currentPort = DEFAULT_PORT
    private var currentHost = DEFAULT_HOST

    override fun onCreate() {
        super.onCreate()
        Timber.i("$TAG Service onCreate() called")

        startForeground(1, notification())
        Timber.d("$TAG Started foreground service with notification")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Timber.d("$TAG Service onStartCommand() called, action=${intent?.action}, startId=$startId")

        when (intent?.action) {
            ACTION_STOP_SERVER -> {
                Timber.i("$TAG Received stop server action, calling stopSelf()")
                stopSelf()
            }
            null -> {
                Timber.d("$TAG onStartCommand with null intent, starting server")
                startServer(intent)
            }
            else -> {
                Timber.d("$TAG onStartCommand with unknown action: ${intent.action}")
                startServer(intent)
            }
        }

        return START_NOT_STICKY
    }

    private fun startServer(intent: Intent?) {
        lifecycleScope.launch {
            Timber.d("$TAG Launching coroutine to start server...")
            try {
                val port = intent?.getIntExtra(EXTRA_PORT, DEFAULT_PORT) 
                    ?: serviceSettingsRepository.mcpServerPort().value
                val host = intent?.getStringExtra(EXTRA_HOST)
                    ?: serviceSettingsRepository.mcpServerHost().value
                currentPort = port
                currentHost = host
                mcpServerManager.start(port, host)
                Timber.i("$TAG Server start completed, isRunning=${mcpServerManager.isRunning}, port=${mcpServerManager.port}")
                startForeground(1, notification())
            } catch (e: Exception) {
                Timber.e(e, "$TAG Failed to start server")
            }
        }
    }

    

    private fun notification(): Notification {
        val port = currentPort
        Timber.d("$TAG Building notification for port $port")
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
        Timber.i("$TAG Service onDestroy() called")
        super.onDestroy()

        lifecycleScope.launch {
            Timber.d("$TAG Launching coroutine to stop server...")
            try {
                mcpServerManager.stop()
                Timber.i("$TAG Server stop completed")
            } catch (e: Exception) {
                Timber.e(e, "$TAG Failed to stop server")
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        Timber.d("$TAG Service onBind() called")
        return null
    }
}

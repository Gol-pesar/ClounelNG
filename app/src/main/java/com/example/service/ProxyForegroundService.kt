package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.PulseProxyApplication
import com.example.R
import com.example.model.EngineStatus
import com.example.proxy.ProxyEngineController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class ProxyForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var telemetryJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                ProxyEngineController.stopEngine()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                val initialNotif = buildNotification("Starting SOCKS5 Tunnel...")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        initialNotif,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, initialNotif)
                }

                serviceScope.launch {
                    val app = application as PulseProxyApplication
                    val activeId = app.preferencesRepository.activeProfileId.first()
                    val allProfiles = app.profileRepository.profiles.first()
                    val profile = allProfiles.firstOrNull { it.id == activeId } ?: allProfiles.firstOrNull()
                    val settings = app.preferencesRepository.settings.first()

                    if (profile == null) {
                        ProxyEngineController.stopEngine()
                        ServiceCompat.stopForeground(this@ProxyForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return@launch
                    }

                    ProxyEngineController.startEngine(profile, settings)
                    observeTelemetryForNotification()
                }
            }
        }
        return START_STICKY
    }

    private fun observeTelemetryForNotification() {
        telemetryJob?.cancel()
        telemetryJob = serviceScope.launch {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            ProxyEngineController.telemetry.collect { t ->
                if (t.status == EngineStatus.STOPPED || t.status == EngineStatus.ERROR) {
                    if (t.status == EngineStatus.STOPPED) {
                        ServiceCompat.stopForeground(this@ProxyForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    return@collect
                }
                val summary = buildString {
                    append("AMIR Mode • ")
                    append(t.targetEndpoint.ifBlank { "Active" })
                    append(" | ↓ ")
                    append(formatBytesCompact(t.totalBytesDown))
                    append(" ↑ ")
                    append(formatBytesCompact(t.totalBytesUp))
                }
                nm.notify(NOTIFICATION_ID, buildNotification(summary))
            }
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ProxyForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_clounel_notif)
            .setContentTitle(getString(R.string.notif_title_running))
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPending)
            .addAction(0, getString(R.string.notif_action_stop), stopPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        telemetryJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "pulse_proxy_tunnel_channel"
        const val NOTIFICATION_ID = 40446
        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"

        fun start(context: Context) {
            val intent = Intent(context, ProxyForegroundService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ProxyForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        private fun formatBytesCompact(bytes: Long): String {
            return when {
                bytes < 1024L -> "${bytes}B"
                bytes < 1024L * 1024L -> String.format(Locale.US, "%.1fKB", bytes / 1024.0)
                bytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1fMB", bytes / (1024.0 * 1024.0))
                else -> String.format(Locale.US, "%.2fGB", bytes / (1024.0 * 1024.0 * 1024.0))
            }
        }
    }
}

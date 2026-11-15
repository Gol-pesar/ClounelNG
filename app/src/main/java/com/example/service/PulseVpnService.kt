package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.PulseProxyApplication
import com.example.R
import com.example.model.EngineStatus
import com.example.model.LogLevel
import com.example.proxy.ProxyEngineController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.amnezia.awg.hevtunnel.TProxyService
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-device Android VpnService that establishes tun0 and routes all device TCP & DNS
 * traffic through hev-socks5-tunnel -> local Socks5ProxyServer -> Cl2 WSS / Direct SOCKS5.
 */
class PulseVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var vpnInterface: ParcelFileDescriptor? = null
    private var telemetryJob: Job? = null
    private val isTunnelRunning = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdownVpn()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                promoteToForeground(buildNotification("Initializing Device VPN Tunnel..."))
                serviceScope.launch {
                    startFullDeviceTunnel()
                }
            }
        }
        return START_STICKY
    }

    private suspend fun startFullDeviceTunnel() {
        if (isTunnelRunning.getAndSet(true)) {
            // Restart cleanly with newest profile/settings
            stopTProxyAndInterface()
        }

        val app = application as PulseProxyApplication
        val activeId = app.preferencesRepository.activeProfileId.first()
        val allProfiles = app.profileRepository.profiles.first()
        val profile = allProfiles.firstOrNull { it.id == activeId } ?: allProfiles.firstOrNull()
        val settings = app.preferencesRepository.settings.first()

        if (profile == null) {
            shutdownVpn()
            return
        }

        // 1. Start the local SOCKS5 / Cl2 WSS engine first so 127.0.0.1:port is listening
        ProxyEngineController.startEngine(
            profile = profile,
            settings = settings,
            isVpnTunnelActive = true
        )

        if (ProxyEngineController.telemetry.value.status == EngineStatus.ERROR) {
            isTunnelRunning.set(false)
            shutdownVpn()
            return
        }

        // 2. Establish Android TUN interface with self-app exclusion to prevent routing loops
        try {
            val builder = Builder()
                .setSession("Clounel • ${profile.name}")
                .setMtu(8500)
                .addAddress("198.18.0.1", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("198.18.0.2")

            runCatching {
                builder.addDisallowedApplication(packageName)
            }.onFailure { e ->
                ProxyEngineController.onLog(LogLevel.WARN, "addDisallowedApplication warning: ${e.message}")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            val pfd = builder.establish()
            if (pfd == null) {
                ProxyEngineController.onLog(
                    LogLevel.ERROR,
                    "VPN permission missing or revoked (VpnService.establish returned null)"
                )
                shutdownVpn()
                return
            }
            vpnInterface = pfd

            // 3. Write hev-socks5-tunnel YAML config with mapdns for DNS-over-TCP tunneling
            val configFile = File(filesDir, "hevtunnel.yml")
            val yaml = buildString {
                appendLine("tunnel:")
                appendLine("  mtu: 8500")
                appendLine("  ipv4: 198.18.0.1")
                appendLine("socks5:")
                appendLine("  port: ${profile.listenPort}")
                appendLine("  address: 127.0.0.1")
                appendLine("  udp: 'tcp'")
                appendLine("mapdns:")
                appendLine("  address: 198.18.0.2")
                appendLine("  port: 53")
                appendLine("  network: 100.64.0.0")
                appendLine("  netmask: 255.192.0.0")
                appendLine("  cache-size: 10000")
                appendLine("misc:")
                appendLine("  task-stack-size: 81920")
            }
            configFile.writeText(yaml)

            // 4. Launch native hev-socks5-tunnel packet router on IO dispatcher
            val fdInt = pfd.fd
            serviceScope.launch(Dispatchers.IO) {
                runCatching {
                    TProxyService.TProxyStartService(configFile.absolutePath, fdInt)
                }.onFailure { e ->
                    ProxyEngineController.onLog(LogLevel.ERROR, "TProxy native error: ${e.message}")
                }
            }

            ProxyEngineController.onLog(
                LogLevel.SUCCESS,
                "[VPN] AMIR Mode Full-Device Tunnel Active"
            )
            observeTelemetryForNotification()
        } catch (e: Exception) {
            ProxyEngineController.onLog(LogLevel.ERROR, "VPN start failed: ${e.message}")
            shutdownVpn()
        }
    }

    private fun stopTProxyAndInterface() {
        runCatching { TProxyService.TProxyStopService() }
        runCatching { vpnInterface?.close() }
        vpnInterface = null
    }

    private fun shutdownVpn() {
        isTunnelRunning.set(false)
        telemetryJob?.cancel()
        telemetryJob = null
        stopTProxyAndInterface()
        ProxyEngineController.stopEngine()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        ProxyEngineController.onLog(LogLevel.WARN, "[VPN] System revoked VPN permission")
        shutdownVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        isTunnelRunning.set(false)
        telemetryJob?.cancel()
        stopTProxyAndInterface()
        ProxyEngineController.stopEngine()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun observeTelemetryForNotification() {
        telemetryJob?.cancel()
        telemetryJob = serviceScope.launch {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            ProxyEngineController.telemetry.collect { t ->
                if (t.status == EngineStatus.STOPPED) return@collect
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

    private fun promoteToForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
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

        val stopIntent = Intent(this, PulseVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
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

    companion object {
        const val CHANNEL_ID = "pulse_proxy_tunnel_channel"
        const val NOTIFICATION_ID = 40447
        const val ACTION_START = "com.example.service.VPN_START"
        const val ACTION_STOP = "com.example.service.VPN_STOP"

        fun start(context: Context) {
            val intent = Intent(context, PulseVpnService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PulseVpnService::class.java).apply {
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

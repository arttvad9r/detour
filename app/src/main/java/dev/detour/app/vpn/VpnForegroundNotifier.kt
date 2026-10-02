package dev.detour.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.detour.app.MainActivity
import dev.detour.app.R

internal class VpnForegroundNotifier(private val service: VpnService) {

    companion object {
        // Android caches a channel name by ID; keep the post-rebrand suffix stable.
        private const val CHANNEL_ID = "detour_vpn_2"
        private const val NOTIFICATION_ID = 1
    }

    fun createChannel() {
        service.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                service.getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private val notificationManager: NotificationManager
        get() = service.getSystemService(NotificationManager::class.java)

    // A fresh Builder stamps `when` with the current time; reusing one stamp keeps
    // the shade from re-sorting the entry and re-showing it as new on every tick.
    private var postedAt = 0L
    private var lastText: String? = null

    fun show(text: String) {
        postedAt = System.currentTimeMillis()
        lastText = text
        val notification = build(text)
        if (Build.VERSION.SDK_INT >= 34) {
            service.startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            service.startForeground(NOTIFICATION_ID, notification)
        }
    }

    /** Refreshes the text of an already-foreground notification without alerting. */
    fun update(text: String) {
        if (text == lastText) return
        lastText = text
        notificationManager.notify(NOTIFICATION_ID, build(text))
    }

    private fun build(text: String): Notification {
        val content = PendingIntent.getActivity(
            service,
            0,
            Intent(service, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            service,
            1,
            Intent(service, TriVpnService::class.java).setAction(TriVpnService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(service, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lock)
            .setContentTitle(service.getString(R.string.app_name))
            .setContentText(text)
            .setWhen(postedAt)
            .setShowWhen(false)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, service.getString(R.string.notif_stop), stop)
            .build()
    }
}

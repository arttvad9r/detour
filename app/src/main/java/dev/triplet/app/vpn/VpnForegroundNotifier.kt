package dev.triplet.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.triplet.app.MainActivity
import dev.triplet.app.R

internal class VpnForegroundNotifier(private val service: VpnService) {

    companion object {
        // Android caches a channel name by ID; keep the post-rebrand suffix stable.
        private const val CHANNEL_ID = "detour_vpn_2"
        private const val NOTIFICATION_ID = 1
    }

    private val notificationManager: NotificationManager
        get() = service.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                service.getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    fun show(text: String, allowStop: Boolean = true) {
        val notification = buildNotification(text, allowStop)
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

    fun update(text: String, allowStop: Boolean = true) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text, allowStop))
    }

    private fun buildNotification(text: String, allowStop: Boolean): Notification {
        val content = PendingIntent.getActivity(
            service,
            0,
            Intent(service, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(service, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lock)
            .setContentTitle(service.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (allowStop) {
            val stop = PendingIntent.getService(
                service,
                1,
                Intent(service, TriVpnService::class.java)
                    .setAction(TriVpnService.ACTION_STOP)
                    .putExtra(DETOUR_VPN_EXTRA_STARTED_BY_APP, true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, service.getString(R.string.notif_stop), stop)
        }
        return builder.build()
    }
}

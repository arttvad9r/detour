package dev.detour.app.vpn

import android.content.Context
import android.net.ConnectivityManager
import dev.detour.engine.engine.ProcessResolver
import java.net.InetSocketAddress

/**
 * Host-side UID bridge for the engine (pins.md round 2):
 * ConnectivityManager.getConnectionOwnerUid — VPN-owner privilege, API 29+.
 * Engine expects "<uid> <packageName>" or "".
 */
class TripUidResolver(context: Context) : ProcessResolver {
    private val appContext = context.applicationContext

    override fun resolve(network: String?, srcIP: String?, srcPort: Long,
                         dstIP: String?, dstPort: Long): String =
        try {
            val proto = if (network == "tcp") 6 else 17
            val cm = appContext.getSystemService(ConnectivityManager::class.java)
            val uid = cm.getConnectionOwnerUid(
                proto,
                InetSocketAddress(srcIP, srcPort.toInt()),
                InetSocketAddress(dstIP, dstPort.toInt()),
            )
            if (uid <= 0) "" else nameOf(uid)?.let { "$uid $it" } ?: ""
        } catch (_: Exception) {
            ""
        }

    // getNameForUid может вернуть "pkg:uid"/shared-имя — берём первый токен до ':'.
    private fun nameOf(uid: Int): String? =
        appContext.packageManager.getNameForUid(uid)?.substringBefore(':')?.takeIf { it.isNotEmpty() }
}

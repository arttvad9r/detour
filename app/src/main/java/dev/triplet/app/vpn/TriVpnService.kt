package dev.triplet.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import dev.triplet.app.MainActivity
import dev.triplet.app.R
import dev.triplet.app.core.AppRoute
import dev.triplet.app.core.Attribution
import dev.triplet.app.core.ConfigGenerator
import dev.triplet.app.core.DpiBackend
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.RoutingInput
import dev.triplet.app.core.VlessKeyParser
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.log.ServiceLog
import dev.triplet.engine.engine.Engine
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class TriVpnService : VpnService() {

    companion object {
        const val ACTION_START = "dev.triplet.app.action.START"
        const val ACTION_STOP = "dev.triplet.app.action.STOP"
        const val ACTION_RESTART = "dev.triplet.app.action.RESTART"
        private const val CHANNEL_ID = "triplet_vpn"
        private const val NOTIFICATION_ID = 1
        private val lastTun = AtomicReference<ParcelFileDescriptor?>(null)
    }

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var store: RoutesStore
    private lateinit var dpi: DpiBackend
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        store = RoutesStore(this)
        dpi = DpiBackend(this)
        createChannel()
        registerNetworkMonitor()
        // Мост атрибуции обязан быть зарегистрирован до Engine.start (pins.md round 2).
        Engine.setProcessResolver(TripUidResolver(applicationContext))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> executor.execute { startSequence() }
            ACTION_STOP -> executor.execute { stopSequence(stopSelf = true) }
            ACTION_RESTART -> executor.execute { stopSequence(stopSelf = false); startSequence() }
            // Sticky-restart приходит с null intent: сервис не нужен без явного старта UI.
            null -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    override fun onRevoke() {
        ServiceLog.w("vpn permission revoked")
        executor.execute { stopSequence(stopSelf = true) }
    }

    override fun onDestroy() {
        unregisterNetworkMonitor()
        executor.execute { stopSequence(stopSelf = false) }
        executor.shutdown()
        super.onDestroy()
    }

    // ---- sequence -------------------------------------------------------

    private fun startSequence() {
        val current = VpnController.state.value
        if (current == VpnState.Active || current == VpnState.Starting) return
        VpnController.setState(VpnState.Starting)
        goForeground(getString(R.string.notif_starting))

        // Executor-поток, блокировка допустима.
        val settings = runBlocking { store.snapshot() }

        // 1. Ключ: если задан — парсим. Невалидный = Fail без попыток.
        val profile = if (settings.vlessUri.isBlank()) null
        else when (val parsed = VlessKeyParser.parse(settings.vlessUri)) {
            is ParseResult.Ok -> parsed.profile
            is ParseResult.Err -> {
                VpnController.setState(VpnState.Failed(getString(R.string.err_invalid_key)))
                stopSequence(stopSelf = true)
                return
            }
        }
        val vpnApps = settings.routes.filterValues { it == AppRoute.VPN }.keys
        if (profile == null && vpnApps.isNotEmpty()) {
            VpnController.setState(VpnState.Failed(getString(R.string.err_key_required)))
            stopSequence(stopSelf = true)
            return
        }

        // 2. ByeDPI нужен, если есть DPI-приложения.
        val dpiApps = settings.routes.filterValues { it == AppRoute.DPI }.keys
        if (dpiApps.isNotEmpty()) {
            ServiceLog.i("dpi: starting (${settings.preset.id})")
            if (!dpi.start(settings.preset, 10808)) {
                VpnController.setState(VpnState.Failed(getString(R.string.err_dpi_failed)))
                stopSequence(stopSelf = true)
                return
            }
        }

        // 3. UID-резолв выбранного; несуществующие пакеты выкидываем,
        //    иначе ConfigGenerator.require()/allow-list уронят конфиг.
        val selected = vpnApps + dpiApps
        val vpnUids = selected.associateWith { uidOf(it) }
            .filterValues { it != null }.mapValues { it.value!! }
        (selected - vpnUids.keys).forEach {
            ServiceLog.w("route skipped, package not found: $it")
        }
        val effVpn = vpnApps intersect vpnUids.keys
        val effDpi = dpiApps intersect vpnUids.keys

        // 4. TUN + движок.
        val fd = try {
            openTun(if (vpnUids.isEmpty()) emptySet() else vpnUids.keys)
        } catch (e: Exception) {
            VpnController.setState(VpnState.Failed(e.message ?: "tun error"))
            stopSequence(stopSelf = true)
            return
        }

        val yaml = ConfigGenerator.build(
            RoutingInput(
                tunFd = fd, apiLevel = Build.VERSION.SDK_INT, ownPackage = packageName,
                profile = profile, vpnApps = effVpn, vpnUids = vpnUids,
                dpiApps = effDpi, attribution = Attribution.UID,
            ),
        )
        val logPath = File(cacheDir, "mihomo.log").absolutePath
        try {
            Engine.start(yaml, logPath)
        } catch (e: Exception) {
            ServiceLog.e("engine: ${e.message}")
            VpnController.setState(VpnState.Failed(getString(R.string.err_engine)))
            stopSequence(stopSelf = true)
            return
        }

        // Одна повторная попытка health-check, затем честная ошибка.
        val healthy = HealthCheck.generate204(10809) || HealthCheck.generate204(10809)
        if (!healthy) {
            VpnController.setState(VpnState.Failed(getString(R.string.err_no_connect)))
            stopSequence(stopSelf = true)
            return
        }

        VpnController.setState(VpnState.Active)
        goForeground(getString(R.string.notif_active))
        ServiceLog.i("active")
    }

    private fun stopSequence(stopSelf: Boolean) {
        runCatching { Engine.stop() }
        dpi.stop()
        // Владение TUN-fd передано движку (sing-tun закрывает fd сам);
        // detach только снимает java-владение — обычный close дал бы
        // double-close и SIGABRT от fdsan.
        // ponytail: если Engine.start упал до передачи fd, он утечёт до смерти процесса;
        // апгрейд — track-список сырых fd с ручным Os.close().
        lastTun.getAndSet(null)?.let { runCatching { it.detachFd() } }
        if (VpnController.state.value !is VpnState.Failed) VpnController.setState(VpnState.Idle)
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (stopSelf) stopSelf()
        ServiceLog.i("stopped")
    }

    // ---- tun -------------------------------------------------------------

    /** [allowed] пуст => capture-all fallback; иначе только выбранные приложения внутри TUN. */
    private fun openTun(allowed: Set<String>): Int {
        val builder = Builder()
            .setSession("Triplet")
            .setMtu(ConfigGenerator.MTU)
        builder.addAddress(ConfigGenerator.INET4.substringBefore('/'),
            ConfigGenerator.INET4.substringAfter('/').toInt())
        builder.addAddress(ConfigGenerator.INET6.substringBefore('/'),
            ConfigGenerator.INET6.substringAfter('/').toInt())

        // fake-ip DNS-адрес движка должен принадлежать TUN-интерфейсу
        // (mihomo v1.19.x игнорирует tun.inet4-address, см. pins.md).
        builder.addAddress("198.18.0.1", 16)

        builder.addRoute("0.0.0.0", 0)
        builder.addRoute("::", 0)

        if (Build.VERSION.SDK_INT >= 33) {
            ConfigGenerator.LAN_PREFIXES.forEach { prefix ->
                runCatching { builder.excludeRoute(toPrefix(prefix)) }
            }
        }

        allowed.forEach { pkg ->
            try {
                builder.addAllowedApplication(pkg)
            } catch (e: Exception) {
                ServiceLog.w("allow-list: $pkg: ${e.message}")
            }
        }
        builder.addDisallowedApplication(packageName)

        val pfd = builder.establish()
            ?: throw IllegalStateException(getString(R.string.err_vpn_permission))
        lastTun.set(pfd)
        return pfd.fd
    }

    private fun toPrefix(cidr: String): android.net.IpPrefix {
        val slash = cidr.indexOf('/')
        val addr = InetAddress.getByName(cidr.substring(0, slash))
        return android.net.IpPrefix(addr, cidr.substring(slash + 1).toInt())
    }

    private fun uidOf(pkg: String): Int? = runCatching {
        packageManager.getPackageUid(pkg, 0)
    }.getOrNull()

    // ---- network monitor --------------------------------------------------

    private fun registerNetworkMonitor() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                ServiceLog.i("network: available, restarting tunnel if active")
                // Перепосылка интента безопасна при любом состоянии.
                VpnController.restartIfActive(this@TriVpnService)
            }
        }
        cm.registerDefaultNetworkCallback(cb)
        netCallback = cb
    }

    private fun unregisterNetworkMonitor() {
        netCallback?.let {
            getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it)
        }
        netCallback = null
    }

    // ---- notification ------------------------------------------------------

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun goForeground(text: String) {
        val content = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TriVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Triplet")
            .setContentText(text)
            .setContentIntent(content)
            .setOngoing(true)
            .addAction(0, getString(R.string.notif_stop), stop)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }
}

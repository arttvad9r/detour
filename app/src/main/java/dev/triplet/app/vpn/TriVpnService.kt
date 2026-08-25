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
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import dev.triplet.app.MainActivity
import dev.triplet.app.R
import dev.triplet.app.core.AppRoute
import dev.triplet.app.core.ConfigGenerator
import dev.triplet.app.core.DnsOptions
import dev.triplet.app.core.DpiArgs
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

class TriVpnService : VpnService() {

    companion object {
        const val ACTION_START = "dev.triplet.app.action.START"
        const val ACTION_STOP = "dev.triplet.app.action.STOP"
        const val ACTION_RESTART = "dev.triplet.app.action.RESTART"
        // "-2": Android кэширует имя канала по ID; суффикс после ребрендинга
        private const val CHANNEL_ID = "detour_vpn_2"
        private const val NOTIFICATION_ID = 1
    }

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var store: RoutesStore
    private lateinit var dpi: DpiBackend
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        // Один DataStore на файл: используем синглет из Application, иначе
        // второй экземпляр RoutesStore падает с IllegalStateException при старте VPN.
        store = (applicationContext as dev.triplet.app.TripletApp).routesStore
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
            if (!dpi.start(DpiArgs.resolve(settings.preset, settings.dpiCustomArgs), 10808)) {
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
                tunFd = fd, apiLevel = Build.VERSION.SDK_INT,
                profile = profile, vpnApps = effVpn, vpnUids = vpnUids,
                dpiApps = effDpi,
                nameserver = DnsOptions.resolve(settings.dnsId, settings.dnsCustom),
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
        // Владение TUN-fd полностью у движка: detachFd выполнен в openTun
        // ДО передачи (иначе между Engine.stop() -> close(fd) и нашим
        // detachFd() освободившийся номер мог занять RenderThread под fence
        // — fdsan абортнул весь процесс, см. креш 22:05 на OnePlus).
        lastNetwork = null
        if (VpnController.state.value !is VpnState.Failed) VpnController.setState(VpnState.Idle)
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (stopSelf) stopSelf()
        ServiceLog.i("stopped")
    }

    // ---- tun -------------------------------------------------------------

    /** [allowed] пуст => capture-all fallback; иначе только выбранные приложения внутри TUN. */
    private fun openTun(allowed: Set<String>): Int {
        val builder = Builder()
            .setSession("Detour")
            .setMtu(ConfigGenerator.MTU)
        builder.addAddress(ConfigGenerator.INET4.substringBefore('/'),
            ConfigGenerator.INET4.substringAfter('/').toInt())
        builder.addAddress(ConfigGenerator.INET6.substringBefore('/'),
            ConfigGenerator.INET6.substringAfter('/').toInt())

        // fake-ip DNS-адрес движка должен принадлежать TUN-интерфейсу
        // (mihomo v1.19.x игнорирует tun.inet4-address, см. pins.md).
        builder.addAddress("198.18.0.1", 16)

        builder.addRoute("0.0.0.0", 0)
        // IPv6 в TUN не маршрутизируем (как в ByeByeDPI): приложения мгновенно
        // видят отсутствие v6 и идут по IPv4. Захват ::/0 с REJECT-правилами
        // давал бесконечные v6-ретраи вместо быстрого отката (приёмка OnePlus).

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
        if (allowed.isEmpty()) {
            // capture-all fallback: exclude self so the engine's own traffic bypasses TUN;
            // with a non-empty allow-list self is already outside it, and Android forbids
            // mixing allowed+disallowed on one Builder (ISE "addAllowedApplication
            // already called").
            builder.addDisallowedApplication(packageName)
        }

        val pfd = builder.establish()
            ?: throw IllegalStateException(getString(R.string.err_vpn_permission))
        // detach СРАЗУ: убирает java-владение из fdsan до передачи в движок.
        // Движок закроет fd сам при остановке; двойного close нет.
        val fd = pfd.detachFd()
        runCatching { pfd.close() }
        return fd
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

    private var lastNetwork: Network? = null

    private fun registerNetworkMonitor() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // VPN establishment/validation re-fires onAvailable for the
                // same or the VPN network itself; restarting on that caused an
                // infinite stop/start storm. Restart only when the underlying
                // default network actually changes (Wi-Fi <-> LTE).
                val caps = cm.getNetworkCapabilities(network) ?: return
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return
                if (network == lastNetwork) return
                lastNetwork = network
                if (VpnController.state.value != VpnState.Active) return
                ServiceLog.i("network changed, restarting tunnel")
                executor.execute { stopSequence(stopSelf = false); startSequence() }
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
            .setContentTitle("Detour")
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

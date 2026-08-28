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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

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
    private val healthExecutor = Executors.newSingleThreadExecutor()
    private val validationGeneration = AtomicInteger(0)
    private val restartQueued = AtomicBoolean(false)
    private val stopQueued = AtomicBoolean(false)
    private val destroyed = AtomicBoolean(false)
    private val lifecycleLock = Any()
    private lateinit var store: RoutesStore
    private lateinit var dpi: DpiBackend
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        destroyed.set(false)
        // Один DataStore на файл: используем синглет из Application, иначе
        // второй экземпляр RoutesStore падает с IllegalStateException при старте VPN.
        store = (applicationContext as dev.triplet.app.TripletApp).routesStore
        dpi = DpiBackend(this) {
            runCatching {
                executor.execute {
                    if (!destroyed.get() && VpnController.state.value == VpnState.Active) {
                        ServiceLog.e("dpi: process exited unexpectedly")
                        VpnController.setState(VpnState.Failed(getString(R.string.err_dpi_failed)))
                        stopSequence(stopSelf = true)
                    }
                }
            }
        }
        createChannel()
        registerNetworkMonitor()
        // Мост атрибуции обязан быть зарегистрирован до Engine.start (pins.md round 2).
        Engine.setProcessResolver(TripUidResolver(applicationContext))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> executor.execute { startSequence() }
            ACTION_STOP -> {
                stopQueued.set(true)
                executor.execute { restartQueued.set(false); stopSequence(stopSelf = true); stopQueued.set(false) }
            }
            ACTION_RESTART -> if (!stopQueued.get() && restartQueued.compareAndSet(false, true)) {
                executor.execute {
                    restartQueued.set(false)
                    if (!stopQueued.get()) { stopSequence(stopSelf = false); startSequence() }
                }
            }
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
        destroyed.set(true)
        validationGeneration.incrementAndGet()
        unregisterNetworkMonitor()
        lastNetwork = null
        stopQueued.set(true)
        healthExecutor.shutdownNow()
        executor.shutdownNow()
        stopSequence(stopSelf = false)
        super.onDestroy()
    }

    // ---- sequence -------------------------------------------------------

    private fun startSequence() {
        synchronized(lifecycleLock) {
            try {
                startSequenceInternal()
            } catch (e: Exception) {
                if (e is InterruptedException) Thread.currentThread().interrupt()
                ServiceLog.e("startup: ${e.message}")
                VpnController.setState(VpnState.Failed(e.message ?: getString(R.string.err_engine)))
                stopSequence(stopSelf = true)
            }
        }
    }

    private fun startSequenceInternal() {
        if (destroyed.get() || stopQueued.get()) return
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
        val installed = settings.routes.keys.associateWith { uidOf(it) }
        val uidPackages = packageManager.getInstalledApplications(0)
            .groupBy { it.uid }
            .mapValues { (_, apps) -> apps.map { it.packageName }.toSet() }
        val effective = effectiveRoutes(settings.routes, installed, uidPackages)
        if (effective.sharedUidConflict.isNotEmpty()) {
            VpnController.setState(VpnState.Failed(getString(R.string.err_shared_uid)))
            stopSequence(stopSelf = true)
            return
        }
        if (effective.isEmpty) {
            VpnController.setState(VpnState.Idle)
            stopSequence(stopSelf = true)
            return
        }
        val vpnApps = effective.vpnPackages
        if (profile == null && vpnApps.isNotEmpty()) {
            VpnController.setState(VpnState.Failed(getString(R.string.err_key_required)))
            stopSequence(stopSelf = true)
            return
        }

        // 2. ByeDPI нужен, если есть DPI-приложения.
        val dpiApps = effective.dpiPackages
        if (dpiApps.isNotEmpty()) {
            ServiceLog.i("dpi: starting (${settings.preset.id})")
            if (settings.preset == dev.triplet.app.core.DpiPreset.CUSTOM &&
                !DpiArgs.isValid(settings.dpiCustomArgs)
            ) {
                VpnController.setState(VpnState.Failed(getString(R.string.err_dpi_failed)))
                stopSequence(stopSelf = true)
                return
            }
            if (!dpi.start(
                    DpiArgs.resolve(settings.preset, settings.dpiCustomArgs), 10808,
                    cancelled = { stopQueued.get() || destroyed.get() },
                )) {
                VpnController.setState(VpnState.Failed(getString(R.string.err_dpi_failed)))
                stopSequence(stopSelf = true)
                return
            }
        }

        // 3. UID-резолв выбранного; несуществующие пакеты выкидываем,
        //    иначе ConfigGenerator.require()/allow-list уронят конфиг.
        val selected = effective.packages
        val vpnUids = selected.associateWith { installed[it]!! }
        (settings.routes.keys - selected).forEach {
            ServiceLog.w("route skipped, package not found: $it")
        }
        val effVpn = vpnApps intersect vpnUids.keys
        val effDpi = dpiApps intersect vpnUids.keys

        // 4. TUN + движок. Keep the detached descriptor explicitly owned until
        // Engine.start returns; config/build failures must not leak it.
        var fd: Int? = null
        var engineAdopted = false
        try {
            val tunFd = openTun(vpnUids.keys)
            fd = tunFd
            val yaml = ConfigGenerator.build(
                RoutingInput(
                    tunFd = tunFd, apiLevel = Build.VERSION.SDK_INT,
                    profile = profile, vpnApps = effVpn, vpnUids = vpnUids,
                    dpiApps = effDpi,
                    nameserver = DnsOptions.resolve(settings.dnsId, settings.dnsCustom),
                ),
            )
            val logPath = File(cacheDir, "mihomo.log").absolutePath
            Engine.start(yaml, logPath)
            engineAdopted = true
            check(Engine.ready()) { "engine TUN is not ready" }
        } catch (e: Exception) {
            ServiceLog.e("engine: ${e.message}")
            VpnController.setState(VpnState.Failed(getString(R.string.err_engine)))
            if (engineAdopted) runCatching { Engine.stop() }
            else fd?.let { closeDetachedFd(it) }
            dpi.stop()
            stopSequence(stopSelf = true)
            return
        }

        // Android TUN and the engine are established now. This is the state the
        // user and the OS perceive as connected; route probes validate it after
        // activation and must not keep the UI or lifecycle executor in Starting.
        VpnController.setState(VpnState.Active)
        runBlocking { store.setSessionStartedAt(System.currentTimeMillis()) }
        goForeground(getString(R.string.notif_active))
        ServiceLog.i("active; validating routes")
        validateRoutesAsync(effVpn, effDpi)
    }

    private fun validateRoutesAsync(effVpn: Set<String>, effDpi: Set<String>) {
        val generation = validationGeneration.incrementAndGet()
        runCatching {
            healthExecutor.execute {
                val cancelled = {
                    destroyed.get() || stopQueued.get() ||
                        validationGeneration.get() != generation ||
                        VpnController.state.value != VpnState.Active
                }
                val vpnHealthy = effVpn.isEmpty() || HealthCheck.generate204(10810, cancelled = cancelled)
                val dpiHealthy = effDpi.isEmpty() ||
                    (dpi.isAlive() && HealthCheck.generate204(10811, cancelled = cancelled))
                ServiceLog.i("probe results: vless=$vpnHealthy dpi=$dpiHealthy")
                if (cancelled() || (vpnHealthy && dpiHealthy)) return@execute

                runCatching {
                    executor.execute {
                        if (
                            validationGeneration.get() == generation &&
                            !destroyed.get() && !stopQueued.get() &&
                            VpnController.state.value == VpnState.Active
                        ) {
                            ServiceLog.e("route validation failed")
                            VpnController.setState(VpnState.Failed(getString(R.string.err_no_connect)))
                            stopSequence(stopSelf = true)
                        }
                    }
                }
            }
        }
    }

    private fun stopSequence(stopSelf: Boolean) {
        synchronized(lifecycleLock) {
            validationGeneration.incrementAndGet()
            runCatching { runBlocking { store.setSessionStartedAt(null) } }
            runCatching { Engine.stop() }
            dpi.stop()
        // Владение TUN-fd полностью у движка: detachFd выполнен в openTun
        // ДО передачи (иначе между Engine.stop() -> close(fd) и нашим
        // detachFd() освободившийся номер мог занять RenderThread под fence
        // — fdsan абортнул весь процесс, см. креш 22:05 на OnePlus).
            if (VpnController.state.value !is VpnState.Failed) VpnController.setState(VpnState.Idle)
            stopForeground(STOP_FOREGROUND_REMOVE)
            if (stopSelf) stopSelf()
            ServiceLog.i("stopped")
        }
    }

    // ---- tun -------------------------------------------------------------

    /** Only the non-empty effective allow-list is ever passed here. */
    private fun openTun(allowed: Set<String>): Int {
        val builder = Builder()
            .setSession("Detour")
            .setMtu(ConfigGenerator.MTU)
        builder.addAddress(ConfigGenerator.INET4.substringBefore('/'),
            ConfigGenerator.INET4.substringAfter('/').toInt())

        // fake-ip DNS-адрес движка должен принадлежать TUN-интерфейсу
        // (mihomo v1.19.x игнорирует tun.inet4-address, см. pins.md).
        builder.addAddress("198.18.0.1", 16)

        builder.addRoute("0.0.0.0", 0)
        // Capture IPv6 too; mihomo explicitly rejects it before the fallback rule.
        builder.addRoute("::", 0)

        if (Build.VERSION.SDK_INT >= 33) {
            ConfigGenerator.LAN_PREFIXES.forEach { prefix ->
                runCatching { builder.excludeRoute(toPrefix(prefix)) }
            }
        }

        var added = 0
        allowed.forEach { pkg ->
            try {
                builder.addAllowedApplication(pkg)
                added++
            } catch (e: Exception) {
                throw IllegalStateException("cannot add routed package $pkg", e)
            }
        }
        check(added > 0) { "empty effective VPN allow-list" }
        val pfd = builder.establish()
            ?: throw IllegalStateException(getString(R.string.err_vpn_permission))
        // detach СРАЗУ: убирает java-владение из fdsan до передачи в движок.
        // Движок закроет fd сам при остановке; двойного close нет.
        val fd = pfd.detachFd()
        runCatching { pfd.close() }
        return fd
    }

    private fun closeDetachedFd(fd: Int) {
        runCatching { ParcelFileDescriptor.adoptFd(fd).close() }
    }

    @androidx.annotation.RequiresApi(33)
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

        // Seed the currently active underlying network before registering the
        // callback. The first onAvailable() is an initial snapshot, not a network
        // change, and must never restart a tunnel that just became Active.
        lastNetwork = cm.activeNetwork?.takeIf { network ->
            cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) != true
        }

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val caps = cm.getNetworkCapabilities(network) ?: return
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return

                val previous = lastNetwork
                lastNetwork = network
                if (previous == null || network == previous) return
                if (VpnController.state.value != VpnState.Active) return

                ServiceLog.i("underlying network changed, restarting tunnel")
                if (!stopQueued.get() && restartQueued.compareAndSet(false, true)) executor.execute {
                    restartQueued.set(false)
                    if (!stopQueued.get()) { stopSequence(stopSelf = false); startSequence() }
                }
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

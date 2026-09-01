package dev.triplet.app.vpn

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import dev.triplet.app.R
import dev.triplet.app.core.AppRoute
import dev.triplet.app.core.ConfigGenerator
import dev.triplet.app.core.DnsOptions
import dev.triplet.app.core.DpiArgs
import dev.triplet.app.core.DpiBackend
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.ProbeAuth
import dev.triplet.app.core.ProbeCredentials
import dev.triplet.app.core.RoutingInput
import dev.triplet.app.core.VlessKeyParser
import dev.triplet.app.core.VpnOutbound
import dev.triplet.app.core.VpnProfileKind
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
    private lateinit var foreground: VpnForegroundNotifier
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
        foreground = VpnForegroundNotifier(this)
        foreground.createChannel()
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
        stopQueued.set(true)
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
        foreground.show(getString(R.string.notif_starting))

        // Executor-поток, блокировка допустима.
        val settings = runBlocking { store.snapshot() }
        // Capture loopback auth once so ByeDPI, mihomo and validation probes
        // cannot diverge even if credential generation changes in the future.
        val probeCredentials = ProbeAuth.current()

        // 1. Разрешаем только выбранный VPN-профиль. VLESS парсится заново перед
        // запуском, WARP уже был строго проверен при импорте/чтении из DataStore.
        val vpn = when (settings.activeVpn) {
            VpnProfileKind.VLESS -> {
                if (settings.vlessUri.isBlank()) null
                else when (val parsed = VlessKeyParser.parse(settings.vlessUri)) {
                    is ParseResult.Ok -> VpnOutbound.Vless(parsed.profile)
                    is ParseResult.Err -> {
                        VpnController.setState(VpnState.Failed(getString(R.string.err_invalid_key)))
                        stopSequence(stopSelf = true)
                        return
                    }
                }
            }
            VpnProfileKind.WARP -> settings.warpProfile?.let(VpnOutbound::Warp)
        }

        val resolvedRoutes = resolveRouteSnapshot(packageManager, settings.routes)
        val installed = resolvedRoutes.installedUids
        val effective = resolvedRoutes.effective
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
        if (vpn == null && vpnApps.isNotEmpty()) {
            VpnController.setState(VpnState.Failed(getString(R.string.err_vpn_profile_required)))
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
                    credentials = probeCredentials,
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
            ServiceLog.i("engine: tun established fd=$tunFd")
            val yaml = ConfigGenerator.build(
                RoutingInput(
                    tunFd = tunFd, apiLevel = Build.VERSION.SDK_INT,
                    vpn = vpn, vpnApps = effVpn, vpnUids = vpnUids,
                    dpiApps = effDpi,
                    nameserver = DnsOptions.resolve(settings.dnsId, settings.dnsCustom),
                    probeCredentials = probeCredentials,
                ),
            )
            ServiceLog.i("engine: config built bytes=${yaml.length}")
            val logPath = File(cacheDir, "mihomo.log").absolutePath
            Engine.start(yaml, logPath)
            ServiceLog.i("engine: start returned")
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
        foreground.show(getString(R.string.notif_active))
        ServiceLog.i("active; validating routes")
        validateRoutesAsync(effVpn, effDpi, settings.activeVpn, probeCredentials)
    }

    private fun validateRoutesAsync(
        effVpn: Set<String>,
        effDpi: Set<String>,
        vpnKind: VpnProfileKind,
        probeCredentials: ProbeCredentials,
    ) {
        val generation = validationGeneration.incrementAndGet()
        runCatching {
            healthExecutor.execute {
                val cancelled = {
                    destroyed.get() || stopQueued.get() ||
                        validationGeneration.get() != generation ||
                        VpnController.state.value != VpnState.Active
                }
                // WARP may need a little longer on the first connection while the
                // url-test group resolves/chooses an endpoint.
                val vpnTimeout = if (vpnKind == VpnProfileKind.WARP) 5000 else 2500
                val vpnHealthy = effVpn.isEmpty() ||
                    HealthCheck.generate204(
                        10810,
                        timeoutMs = vpnTimeout,
                        cancelled = cancelled,
                        credentials = probeCredentials,
                    )
                val dpiHealthy = effDpi.isEmpty() ||
                    (dpi.isAlive() && HealthCheck.generate204(
                        10811,
                        cancelled = cancelled,
                        credentials = probeCredentials,
                    ))
                ServiceLog.i("probe results: vpn=$vpnHealthy dpi=$dpiHealthy")
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
            .setSession(getString(R.string.app_name))
            .setMtu(ConfigGenerator.MTU)
        builder.addAddress(ConfigGenerator.INET4.substringBefore('/'),
            ConfigGenerator.INET4.substringAfter('/').toInt())

        // Device spike requires mihomo's fake-IP gateway on the host-created TUN.
        // Keep the validated /30; external-FD mode does not configure the interface.
        builder.addAddress("198.18.0.1", 30)

        builder.addRoute("0.0.0.0", 0)
        // Capture IPv6 too; mihomo explicitly rejects it before the fallback rule.
        // Keep this TUN IPv4-only; the engine rejects IPv6 in ConfigGenerator.

        if (Build.VERSION.SDK_INT >= 33) {
            ConfigGenerator.ANDROID_EXCLUDED_PREFIXES.forEach { prefix ->
                builder.excludeRoute(toPrefix(prefix))
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

    // ---- network monitor --------------------------------------------------

    private var lastNetwork: Network? = null

    private fun registerNetworkMonitor() {
        val cm = getSystemService(ConnectivityManager::class.java)

        // Seed the currently active underlying network before registering the
        // callback. The first capabilities callback is an initial snapshot, not a
        // network change, and must never restart a tunnel that just became Active.
        lastNetwork = cm.activeNetwork?.takeIf { network ->
            cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) != true
        }

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                // Android guarantees these capabilities are ordered with the
                // preceding onAvailable callback. Synchronous capability queries
                // from inside onAvailable are explicitly documented as racy.
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return

                val previous = lastNetwork
                lastNetwork = network
                if (previous == null || network == previous) return
                if (VpnController.state.value != VpnState.Active) return

                ServiceLog.i("underlying network changed, restarting tunnel")
                if (
                    destroyed.get() || stopQueued.get() ||
                    !restartQueued.compareAndSet(false, true)
                ) return
                runCatching {
                    executor.execute {
                        restartQueued.set(false)
                        if (!destroyed.get() && !stopQueued.get()) {
                            stopSequence(stopSelf = false)
                            startSequence()
                        }
                    }
                }.onFailure {
                    // onDestroy() can shut the executor down while an already
                    // delivered network callback is still finishing.
                    restartQueued.set(false)
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
}

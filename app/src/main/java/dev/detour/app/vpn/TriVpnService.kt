package dev.detour.app.vpn

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import dev.detour.app.R
import dev.detour.app.DetourApp
import dev.detour.app.core.AppRoute
import dev.detour.app.core.ConfigGenerator
import dev.detour.app.core.DnsOptions
import dev.detour.app.core.DpiArgs
import dev.detour.app.core.DpiBackend
import dev.detour.app.core.ParseResult
import dev.detour.app.core.ProbeAuth
import dev.detour.app.core.ProbeCredentials
import dev.detour.app.core.RoutingInput
import dev.detour.app.core.VlessKeyParser
import dev.detour.app.core.VpnOutbound
import dev.detour.app.core.VpnProfileKind
import dev.detour.app.data.RoutesStore
import dev.detour.app.log.ServiceLog
import dev.detour.engine.engine.Engine
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class TriVpnService : VpnService() {

    companion object {
        const val ACTION_START = "dev.detour.app.action.START"
        const val ACTION_STOP = "dev.detour.app.action.STOP"
        const val ACTION_RESTART = "dev.detour.app.action.RESTART"
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
        store = (applicationContext as DetourApp).routesStore
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
        // Native shutdown remains synchronous so the TUN and child resources are
        // definitely closed before service destruction. Persistence is delegated
        // to the Application IO scope to avoid DataStore runBlocking on main.
        stopSequence(stopSelf = false, persistSessionSynchronously = false)
        super.onDestroy()
    }

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

        val settings = runBlocking { store.snapshot() }
        val probeCredentials = ProbeAuth.current()

        val vpn = when (settings.activeVpn) {
            VpnProfileKind.VLESS -> {
                if (settings.vlessUri.isBlank()) null
                else when (val parsed = VlessKeyParser.parse(settings.vlessUri)) {
                    is ParseResult.Ok -> {
                        if (parsed.profile.isSubscription) {
                            VpnController.setState(VpnState.Failed(getString(R.string.err_invalid_key)))
                            stopSequence(stopSelf = true)
                            return
                        }
                        VpnOutbound.Vless(parsed.profile)
                    }
                    is ParseResult.Err -> {
                        VpnController.setState(VpnState.Failed(getString(R.string.err_invalid_key)))
                        stopSequence(stopSelf = true)
                        return
                    }
                }
            }
            VpnProfileKind.SUBSCRIPTION -> {
                if (settings.vlessUri.isBlank()) null
                else when (val parsed = VlessKeyParser.parse(settings.vlessUri)) {
                    is ParseResult.Ok -> {
                        val url = parsed.profile.subscriptionUrl
                        if (url == null) {
                            VpnController.setState(VpnState.Failed(getString(R.string.err_invalid_key)))
                            stopSequence(stopSelf = true)
                            return
                        }
                        VpnOutbound.Subscription(
                            url = url,
                            selectedNode = settings.vlessKeys.active?.selectedNode,
                        )
                    }
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

        val dpiApps = effective.dpiPackages
        if (dpiApps.isNotEmpty()) {
            ServiceLog.i("dpi: starting (${settings.preset.id})")
            if (settings.preset == dev.detour.app.core.DpiPreset.CUSTOM &&
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

        val selected = effective.packages
        val vpnUids = selected.associateWith { installed[it]!! }
        (settings.routes.keys - selected).forEach {
            ServiceLog.w("route skipped, package not found: $it")
        }
        val effVpn = vpnApps intersect vpnUids.keys
        val effDpi = dpiApps intersect vpnUids.keys

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

            if (settings.activeVpn == VpnProfileKind.SUBSCRIPTION) {
                val activeKey = settings.vlessKeys.active
                val actualNode = runCatching {
                    Engine.subscriptionSelectedNode(cacheDir.absolutePath)
                }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
                if (activeKey != null && actualNode != null && activeKey.selectedNode != actualNode) {
                    runBlocking {
                        store.updateVlessKey(activeKey.copy(selectedNode = actualNode))
                    }
                    ServiceLog.i("subscription: persisted active node after engine start")
                }
            }
        } catch (e: Exception) {
            ServiceLog.e("engine: ${e.message}")
            VpnController.setState(VpnState.Failed(getString(R.string.err_engine)))
            if (engineAdopted) runCatching { Engine.stop() }
            else fd?.let { closeDetachedFd(it) }
            dpi.stop()
            stopSequence(stopSelf = true)
            return
        }

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
                val vpnTimeout = when (vpnKind) {
                    VpnProfileKind.VLESS -> 2500
                    VpnProfileKind.SUBSCRIPTION, VpnProfileKind.WARP -> 5000
                }
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
                if (cancelled()) return@execute

                if (vpnHealthy && dpiHealthy) {
                    ServiceLog.i("probe results: vpn=true dpi=true")
                } else {
                    ServiceLog.w("route probe failed (non-fatal): vpn=$vpnHealthy dpi=$dpiHealthy")
                }
            }
        }
    }

    private fun stopSequence(
        stopSelf: Boolean,
        persistSessionSynchronously: Boolean = true,
    ) {
        synchronized(lifecycleLock) {
            validationGeneration.incrementAndGet()
            if (persistSessionSynchronously) {
                runCatching { runBlocking { store.setSessionStartedAt(null) } }
            } else {
                (applicationContext as DetourApp).clearVpnSessionTimestampAsync()
            }
            runCatching { Engine.stop() }
            dpi.stop()
            if (VpnController.state.value !is VpnState.Failed) VpnController.setState(VpnState.Idle)
            stopForeground(STOP_FOREGROUND_REMOVE)
            if (stopSelf) stopSelf()
            ServiceLog.i("stopped")
        }
    }

    private fun openTun(allowed: Set<String>): Int {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(ConfigGenerator.MTU)
        builder.addAddress(ConfigGenerator.INET4.substringBefore('/'),
            ConfigGenerator.INET4.substringAfter('/').toInt())

        builder.addAddress("198.18.0.1", 30)

        builder.addRoute("0.0.0.0", 0)
        // Keep this TUN IPv4-only. Android blocks the unconfigured IPv6 family
        // for allowed VPN apps; ConfigGenerator also rejects IPv6 defensively.

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

    private var lastNetwork: Network? = null

    private fun registerNetworkMonitor() {
        val cm = getSystemService(ConnectivityManager::class.java)

        lastNetwork = cm.activeNetwork?.takeIf { network ->
            cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) != true
        }

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
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

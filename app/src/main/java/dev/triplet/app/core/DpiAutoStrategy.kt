package dev.triplet.app.core

/**
 * A concrete HTTPS endpoint selected for DPI strategy testing.
 * [scopeHost] is the host suffix the resulting per-domain rule should cover;
 * it may be broader than the concrete [host] used by the probe.
 */
data class DpiProbeTarget(
    val id: String,
    val host: String,
    val scopeHost: String = host,
) {
    init {
        require(id.isNotBlank()) { "target id is blank" }
        require(host.isNotBlank()) { "target host is blank" }
        require(scopeHost.isNotBlank()) { "target scope is blank" }
        require(host.none { it.isWhitespace() || it.code < 0x20 || it.code == 0x7f }) {
            "invalid target host"
        }
        require(scopeHost.none { it.isWhitespace() || it.code < 0x20 || it.code == 0x7f }) {
            "invalid target scope"
        }
    }
}

/** A trusted, app-authored ByeDPI argument set eligible for automatic testing. */
data class DpiStrategyCandidate(
    val id: String,
    val args: List<String>,
) {
    init {
        require(id.isNotBlank()) { "candidate id is blank" }
        require(args.isNotEmpty()) { "candidate args are empty" }
        require(args.none { it.isBlank() || it.any { c -> c.code < 0x20 || c.code == 0x7f } }) {
            "invalid candidate args"
        }
    }

    val complexity: Int get() = args.size
}

/** Single end-to-end observation produced by the transport-specific probe. */
data class DpiProbeAttempt(
    val success: Boolean,
    val latencyMs: Long? = null,
) {
    init {
        require(latencyMs == null || latencyMs >= 0L) { "negative latency" }
    }
}

/** Aggregated observations for one target under one strategy. */
data class DpiTargetResult(
    val target: DpiProbeTarget,
    val attempts: Int,
    val successes: Int,
    val successfulLatenciesMs: List<Long> = emptyList(),
) {
    init {
        require(attempts >= 0) { "negative attempt count" }
        require(successes in 0..attempts) { "success count exceeds attempts" }
        require(successfulLatenciesMs.size <= successes) { "too many latency samples" }
        require(successfulLatenciesMs.all { it >= 0L }) { "negative latency sample" }
    }

    val fullyWorking: Boolean get() = attempts > 0 && successes == attempts
    val reachable: Boolean get() = successes > 0

    val medianLatencyMs: Long?
        get() {
            if (successfulLatenciesMs.isEmpty()) return null
            val sorted = successfulLatenciesMs.sorted()
            return sorted[(sorted.size - 1) / 2]
        }
}

/** Complete result for a candidate. */
data class DpiStrategyResult(
    val candidate: DpiStrategyCandidate,
    val backendStarted: Boolean,
    val targets: List<DpiTargetResult>,
) {
    val fullyWorkingTargets: Int get() = targets.count { it.fullyWorking }
    val reachableTargets: Int get() = targets.count { it.reachable }
    val totalSuccesses: Int get() = targets.sumOf { it.successes }
    val totalAttempts: Int get() = targets.sumOf { it.attempts }

    val medianLatencyMs: Long?
        get() {
            val values = targets.flatMap { it.successfulLatenciesMs }.sorted()
            if (values.isEmpty()) return null
            return values[(values.size - 1) / 2]
        }
}

/**
 * App-authored candidate catalog for the bundled ByeDPI v0.17.3 binary.
 * It is deliberately independent from the narrow user CUSTOM validator: AUTO
 * may use additional upstream-documented strategy primitives while persisted
 * selection remains a trusted candidate ID rather than arbitrary argv.
 *
 * The list is independently authored from ByeDPI's upstream documentation and
 * does not copy ByeByeDPI's GPL proxytest strategy asset.
 */
object DpiStrategyCatalog {
    private val forbiddenProcessOptions = setOf(
        "-i", "--ip", "-p", "--port", "-D", "--daemon", "-w", "--pidfile",
        "-E", "--transparent", "-U", "--no-udp", "-J",
    )

    val default: List<DpiStrategyCandidate> = listOf(
        DpiStrategyCandidate(
            id = "recommended",
            args = DpiPreset.RECOMMENDED.args,
        ),
        DpiStrategyCandidate(
            id = "disorder-1",
            args = listOf("-d", "1", "--timeout", "3"),
        ),
        DpiStrategyCandidate(
            id = "split-sni",
            args = listOf("-s", "1+s", "--timeout", "3"),
        ),
        DpiStrategyCandidate(
            id = "split-disorder-sni",
            args = listOf("-s", "1+s", "-d", "3+s", "--timeout", "3"),
        ),
        DpiStrategyCandidate(
            id = "split-middle-sni",
            args = listOf("-s", "0+sm", "--timeout", "3"),
        ),
        DpiStrategyCandidate(
            id = "tls-record-sni",
            args = listOf("-r", "1+s", "--timeout", "3"),
        ),
        DpiStrategyCandidate(
            id = "oob-sni",
            args = listOf("-o", "1+s", "--timeout", "3"),
        ),
        DpiStrategyCandidate(
            id = "disoob-sni",
            args = listOf("-q", "1+s", "--timeout", "3"),
        ),
    ).also { candidates ->
        check(candidates.map { it.id }.distinct().size == candidates.size)
        check(candidates.none { candidate -> candidate.args.any { it in forbiddenProcessOptions } })
    }

    fun byId(id: String): DpiStrategyCandidate? = default.firstOrNull { it.id == id }
}

/** Runtime adapter for starting and stopping a candidate-specific local proxy. */
interface DpiStrategyBackend {
    fun start(candidate: DpiStrategyCandidate): Boolean
    fun stop()
}

/** Runtime adapter for one target check through the currently running proxy. */
fun interface DpiTargetProbe {
    fun probe(target: DpiProbeTarget): DpiProbeAttempt
}

/**
 * Synchronous search primitive. Callers own the worker thread/coroutine.
 * The backend is always stopped between candidates, including failures.
 */
class DpiStrategySearchRunner(
    private val backend: DpiStrategyBackend,
    private val probe: DpiTargetProbe,
) {
    fun run(
        candidates: List<DpiStrategyCandidate>,
        targets: List<DpiProbeTarget>,
        attemptsPerTarget: Int = 2,
        stopCandidateOnFailure: Boolean = false,
        cancelled: () -> Boolean = { false },
    ): List<DpiStrategyResult> {
        require(candidates.isNotEmpty()) { "candidate list is empty" }
        require(targets.isNotEmpty()) { "target list is empty" }
        require(attemptsPerTarget > 0) { "attempt count must be positive" }
        require(candidates.map { it.id }.distinct().size == candidates.size) { "duplicate candidate id" }
        require(targets.map { it.id }.distinct().size == targets.size) { "duplicate target id" }

        val results = mutableListOf<DpiStrategyResult>()

        for (candidate in candidates) {
            if (cancelled()) break

            try {
                val started = backend.start(candidate)
                if (!started) {
                    results += DpiStrategyResult(
                        candidate = candidate,
                        backendStarted = false,
                        targets = targets.map { DpiTargetResult(it, attempts = 0, successes = 0) },
                    )
                    continue
                }

                val targetResults = mutableListOf<DpiTargetResult>()
                var candidateFailed = false
                for (target in targets) {
                    if (cancelled() || candidateFailed) break

                    var attempts = 0
                    var successes = 0
                    val latencies = mutableListOf<Long>()

                    for (attemptIndex in 0 until attemptsPerTarget) {
                        if (cancelled()) break
                        attempts++
                        val observation = probe.probe(target)
                        if (observation.success) {
                            successes++
                            observation.latencyMs?.let(latencies::add)
                        } else if (stopCandidateOnFailure) {
                            candidateFailed = true
                            break
                        }
                    }

                    targetResults += DpiTargetResult(
                        target = target,
                        attempts = attempts,
                        successes = successes,
                        successfulLatenciesMs = latencies,
                    )
                }

                // Keep result shape stable if cancellation or a global-winner
                // short-circuit stops the candidate before all targets are visited.
                val completedIds = targetResults.asSequence().map { it.target.id }.toSet()
                targetResults += targets
                    .filterNot { it.id in completedIds }
                    .map { DpiTargetResult(it, attempts = 0, successes = 0) }

                results += DpiStrategyResult(
                    candidate = candidate,
                    backendStarted = true,
                    targets = targetResults,
                )
            } finally {
                // stop() is required even when start() returned false: adapters may
                // have partially allocated process resources before reporting failure.
                runCatching { backend.stop() }
            }
        }

        return DpiStrategyRanker.rank(results)
    }
}

/**
 * Prefer coverage and repeatability over raw latency. Latency only breaks ties
 * between strategies that work equally well for the selected targets.
 */
object DpiStrategyRanker {
    fun rank(results: List<DpiStrategyResult>): List<DpiStrategyResult> =
        results.sortedWith(
            compareByDescending<DpiStrategyResult> { it.backendStarted }
                .thenByDescending { it.fullyWorkingTargets }
                .thenByDescending { it.reachableTargets }
                .thenByDescending { it.totalSuccesses }
                .thenBy { it.medianLatencyMs ?: Long.MAX_VALUE }
                .thenBy { it.candidate.complexity }
                .thenBy { it.candidate.id },
        )
}

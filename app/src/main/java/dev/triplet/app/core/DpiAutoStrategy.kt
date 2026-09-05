package dev.triplet.app.core

/** A concrete HTTPS host selected for DPI strategy testing. */
data class DpiProbeTarget(
    val id: String,
    val host: String,
) {
    init {
        require(id.isNotBlank()) { "target id is blank" }
        require(host.isNotBlank()) { "target host is blank" }
        require(host.none { it.isWhitespace() || it.code < 0x20 || it.code == 0x7f }) {
            "invalid target host"
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
 * The automatic catalog is intentionally app-authored rather than copied from
 * ByeByeDPI's GPL Android tester. Every entry is compatible with Detour's
 * pinned ByeDPI v0.17.3 and uses the narrow argument surface already exposed
 * by DpiArgs.
 */
object DpiStrategyCatalog {
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
    ).also { candidates ->
        check(candidates.map { it.id }.distinct().size == candidates.size)
        check(candidates.all { DpiArgs.isValid(it.args.joinToString(" ")) })
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
                for (target in targets) {
                    if (cancelled()) break

                    var attempts = 0
                    var successes = 0
                    val latencies = mutableListOf<Long>()

                    repeat(attemptsPerTarget) {
                        if (cancelled()) return@repeat
                        attempts++
                        val observation = probe.probe(target)
                        if (observation.success) {
                            successes++
                            observation.latencyMs?.let(latencies::add)
                        }
                    }

                    targetResults += DpiTargetResult(
                        target = target,
                        attempts = attempts,
                        successes = successes,
                        successfulLatenciesMs = latencies,
                    )
                }

                // Keep result shape stable if cancellation happens between targets.
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

package dev.triplet.app.core

/** Result of baseline probing followed by strategy testing. */
data class DpiAutoSearchReport(
    val baseline: List<DpiTargetResult>,
    val strategies: List<DpiStrategyResult>,
) {
    val problematicTargets: List<DpiProbeTarget>
        get() = baseline.filterNot { it.fullyWorking }.map { it.target }

    val allDirect: Boolean get() = problematicTargets.isEmpty()

    val winner: DpiStrategyResult?
        get() = strategies.firstOrNull()?.takeIf { result ->
            result.backendStarted &&
                result.targets.isNotEmpty() &&
                result.fullyWorkingTargets == result.targets.size
        }
}

enum class DpiAutoProgressPhase { BASELINE, STRATEGY }

/**
 * Deterministic AUTO-search progress. Counts completed targets during the
 * direct baseline and completed trusted candidates during strategy search.
 * It intentionally does not estimate wall-clock time because network latency
 * and backend start time vary significantly between targets and devices.
 */
data class DpiAutoProgress(
    val phase: DpiAutoProgressPhase,
    val completed: Int,
    val total: Int,
    val currentId: String? = null,
) {
    init {
        require(total > 0) { "progress total must be positive" }
        require(completed in 0..total) { "progress completed is out of range" }
        require(currentId == null || currentId.isNotBlank()) { "progress id is blank" }
    }

    val fraction: Float get() = completed.toFloat() / total.toFloat()
}

fun interface DpiStrategySearcher {
    fun search(targets: List<DpiProbeTarget>, cancelled: () -> Boolean): List<DpiStrategyResult>
}

/**
 * Fast global strategy coordinator. Runs direct baseline first, then puts the
 * failing targets first so the runner can reject bad candidates cheaply. A
 * surviving candidate is still regression-checked against every selected
 * target because the global strategy is not host-scoped at runtime.
 */
class DpiAutoSearchCoordinator(
    private val directProbe: DpiTargetProbe,
    private val strategySearcher: DpiStrategySearcher,
) {
    fun run(
        targets: List<DpiProbeTarget>,
        attemptsPerTarget: Int = 2,
        cancelled: () -> Boolean = { false },
        onProgress: (DpiAutoProgress) -> Unit = {},
    ): DpiAutoSearchReport {
        val baseline = runBaseline(targets, attemptsPerTarget, directProbe, cancelled, onProgress)
        if (cancelled()) return DpiAutoSearchReport(baseline, emptyList())

        val problematic = baseline.filterNot { it.fullyWorking }.map { it.target }
        if (problematic.isEmpty()) return DpiAutoSearchReport(baseline, emptyList())
        val alreadyDirect = baseline.filter { it.fullyWorking }.map { it.target }
        val strategyTargets = problematic + alreadyDirect

        return DpiAutoSearchReport(
            baseline = baseline,
            strategies = strategySearcher.search(strategyTargets, cancelled),
        )
    }
}

/**
 * Per-domain coordinator. If any concrete endpoint in a rule scope fails the
 * direct baseline, every selected endpoint in that same scope remains eligible
 * for retesting under every candidate. Baseline failures are ordered first so
 * ineffective candidates can reject a scope before probing direct-working peers.
 */
class DpiPerDomainSearchCoordinator(
    private val directProbe: DpiTargetProbe,
    private val strategySearcher: DpiStrategySearcher,
) {
    fun run(
        targets: List<DpiProbeTarget>,
        attemptsPerTarget: Int = 2,
        cancelled: () -> Boolean = { false },
        onProgress: (DpiAutoProgress) -> Unit = {},
    ): DpiAutoSearchReport {
        val baseline = runBaseline(targets, attemptsPerTarget, directProbe, cancelled, onProgress)
        if (cancelled()) return DpiAutoSearchReport(baseline, emptyList())

        val affectedScopes = baseline
            .filterNot { it.fullyWorking }
            .map { it.target.scopeHost }
            .toSet()
        if (affectedScopes.isEmpty()) return DpiAutoSearchReport(baseline, emptyList())

        val baselineById = baseline.associateBy { it.target.id }
        val strategyTargets = targets
            .filter { it.scopeHost in affectedScopes }
            .sortedBy { baselineById.getValue(it.id).fullyWorking }
        return DpiAutoSearchReport(
            baseline = baseline,
            strategies = strategySearcher.search(strategyTargets, cancelled),
        )
    }
}

private fun runBaseline(
    targets: List<DpiProbeTarget>,
    attemptsPerTarget: Int,
    directProbe: DpiTargetProbe,
    cancelled: () -> Boolean,
    onProgress: (DpiAutoProgress) -> Unit,
): List<DpiTargetResult> {
    require(targets.isNotEmpty()) { "target list is empty" }
    require(attemptsPerTarget > 0) { "attempt count must be positive" }
    require(targets.map { it.id }.distinct().size == targets.size) { "duplicate target id" }

    val results = mutableListOf<DpiTargetResult>()
    targets.forEachIndexed { index, target ->
        if (cancelled()) {
            results += DpiTargetResult(target, attempts = 0, successes = 0)
            return@forEachIndexed
        }

        onProgress(
            DpiAutoProgress(
                phase = DpiAutoProgressPhase.BASELINE,
                completed = index,
                total = targets.size,
                currentId = target.id,
            ),
        )
        results += aggregateTarget(target, attemptsPerTarget, directProbe, cancelled)
        if (!cancelled()) {
            onProgress(
                DpiAutoProgress(
                    phase = DpiAutoProgressPhase.BASELINE,
                    completed = index + 1,
                    total = targets.size,
                    currentId = target.id,
                ),
            )
        }
    }
    return results
}

internal fun aggregateTarget(
    target: DpiProbeTarget,
    attemptsPerTarget: Int,
    probe: DpiTargetProbe,
    cancelled: () -> Boolean,
): DpiTargetResult {
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
    return DpiTargetResult(
        target = target,
        attempts = attempts,
        successes = successes,
        successfulLatenciesMs = latencies,
    )
}

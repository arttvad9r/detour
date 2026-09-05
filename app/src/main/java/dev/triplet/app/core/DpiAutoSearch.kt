package dev.triplet.app.core

/** Result of baseline probing followed by strategy testing only for blocked targets. */
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

fun interface DpiStrategySearcher {
    fun search(targets: List<DpiProbeTarget>, cancelled: () -> Boolean): List<DpiStrategyResult>
}

/**
 * Runs a direct baseline first and excludes already-working targets from the
 * strategy search. A strategy is considered applicable only if it fully passes
 * every target that failed the baseline.
 */
class DpiAutoSearchCoordinator(
    private val directProbe: DpiTargetProbe,
    private val strategySearcher: DpiStrategySearcher,
) {
    fun run(
        targets: List<DpiProbeTarget>,
        attemptsPerTarget: Int = 2,
        cancelled: () -> Boolean = { false },
    ): DpiAutoSearchReport {
        require(targets.isNotEmpty()) { "target list is empty" }
        require(attemptsPerTarget > 0) { "attempt count must be positive" }
        require(targets.map { it.id }.distinct().size == targets.size) { "duplicate target id" }

        val baseline = targets.map { target ->
            if (cancelled()) return@map DpiTargetResult(target, attempts = 0, successes = 0)
            aggregateTarget(target, attemptsPerTarget, directProbe, cancelled)
        }
        if (cancelled()) return DpiAutoSearchReport(baseline, emptyList())

        val problematic = baseline.filterNot { it.fullyWorking }.map { it.target }
        if (problematic.isEmpty()) return DpiAutoSearchReport(baseline, emptyList())

        return DpiAutoSearchReport(
            baseline = baseline,
            strategies = strategySearcher.search(problematic, cancelled),
        )
    }
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

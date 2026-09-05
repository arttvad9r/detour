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

fun interface DpiStrategySearcher {
    fun search(targets: List<DpiProbeTarget>, cancelled: () -> Boolean): List<DpiStrategyResult>
}

/**
 * Fast global strategy coordinator. Runs direct baseline first and excludes
 * already-working targets from the strategy search. A global strategy must
 * fully pass every target that failed the baseline.
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
        val baseline = runBaseline(targets, attemptsPerTarget, directProbe, cancelled)
        if (cancelled()) return DpiAutoSearchReport(baseline, emptyList())

        val problematic = baseline.filterNot { it.fullyWorking }.map { it.target }
        if (problematic.isEmpty()) return DpiAutoSearchReport(baseline, emptyList())

        return DpiAutoSearchReport(
            baseline = baseline,
            strategies = strategySearcher.search(problematic, cancelled),
        )
    }
}

/**
 * Per-domain coordinator. If any concrete endpoint in a rule scope fails the
 * direct baseline, every selected endpoint in that same scope is retested under
 * every candidate. This prevents a broad host rule from fixing one endpoint
 * while silently breaking another endpoint that was working directly.
 */
class DpiPerDomainSearchCoordinator(
    private val directProbe: DpiTargetProbe,
    private val strategySearcher: DpiStrategySearcher,
) {
    fun run(
        targets: List<DpiProbeTarget>,
        attemptsPerTarget: Int = 2,
        cancelled: () -> Boolean = { false },
    ): DpiAutoSearchReport {
        val baseline = runBaseline(targets, attemptsPerTarget, directProbe, cancelled)
        if (cancelled()) return DpiAutoSearchReport(baseline, emptyList())

        val affectedScopes = baseline
            .filterNot { it.fullyWorking }
            .map { it.target.scopeHost }
            .toSet()
        if (affectedScopes.isEmpty()) return DpiAutoSearchReport(baseline, emptyList())

        val strategyTargets = targets.filter { it.scopeHost in affectedScopes }
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
): List<DpiTargetResult> {
    require(targets.isNotEmpty()) { "target list is empty" }
    require(attemptsPerTarget > 0) { "attempt count must be positive" }
    require(targets.map { it.id }.distinct().size == targets.size) { "duplicate target id" }

    return targets.map { target ->
        if (cancelled()) DpiTargetResult(target, attempts = 0, successes = 0)
        else aggregateTarget(target, attemptsPerTarget, directProbe, cancelled)
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

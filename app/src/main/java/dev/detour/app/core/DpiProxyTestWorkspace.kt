package dev.detour.app.core

data class DpiProxyTestServiceSummary(
    val serviceId: String,
    val hostCount: Int,
    val fullyWorkingHosts: Int,
    val successes: Int,
    val attempts: Int,
) {
    init {
        require(hostCount > 0 && fullyWorkingHosts in 0..hostCount)
        require(attempts >= 0 && successes in 0..attempts)
    }
}

/** User-facing summary kept after a proxy-test run. */
data class DpiProxyTestResultSummary(
    val strategy: DpiProxyTestStrategy,
    val backendStarted: Boolean,
    val completed: Boolean,
    val hostCount: Int,
    val fullyWorkingHosts: Int,
    val totalSuccesses: Int,
    val totalAttempts: Int,
    val medianLatencyMs: Long?,
    val services: List<DpiProxyTestServiceSummary> = emptyList(),
) {
    init {
        require(hostCount >= 0)
        require(fullyWorkingHosts in 0..hostCount)
        require(totalAttempts >= 0)
        require(totalSuccesses in 0..totalAttempts)
        require(medianLatencyMs == null || medianLatencyMs >= 0)
    }

    val canApply: Boolean get() = backendStarted && completed && totalSuccesses > 0

    val fullCoverage: Boolean
        get() = backendStarted && completed && hostCount > 0 && fullyWorkingHosts == hostCount
}

fun DpiProxyTestStrategyResult.toSummary(
    selectedIds: Set<String> = emptySet(),
): DpiProxyTestResultSummary = DpiProxyTestResultSummary(
    strategy = strategy,
    backendStarted = backendStarted,
    completed = completed,
    hostCount = hosts.size,
    fullyWorkingHosts = fullyWorkingHosts,
    totalSuccesses = totalSuccesses,
    totalAttempts = totalAttempts,
    medianLatencyMs = medianLatencyMs,
    services = DpiProxyTestCatalog.domainLists.filter { it.id in selectedIds }.mapNotNull { service ->
        val checks = hosts.filter { it.host in service.hosts }
        if (checks.isEmpty()) null else DpiProxyTestServiceSummary(
            service.id, checks.size, checks.count { it.fullyWorking },
            checks.sumOf { it.successes }, checks.sumOf { it.attempts },
        )
    },
)

/** One completed test session that can be reopened and compared later. */
data class DpiProxyTestRun(
    val id: String,
    val createdAtEpochMs: Long,
    val selectedDomainIds: Set<String>,
    val config: DpiProxyTestConfig,
    val results: List<DpiProxyTestResultSummary>,
) {
    init {
        require(id.isNotBlank())
        require(createdAtEpochMs > 0)
        require(selectedDomainIds.isNotEmpty())
        require(results.isNotEmpty())
        require(results.map { it.strategy.id }.distinct().size == results.size)
    }
}

/** Builds the exact strategy list that a user chose for the next test. */
object DpiProxyTestStrategySelection {
    const val CUSTOM_STRATEGY_ID = "custom-user"

    val defaultReferenceIds: Set<String> = DpiProxyTestCatalog.strategies
        .mapTo(linkedSetOf()) { it.id }

    fun custom(raw: String): DpiProxyTestStrategy? {
        val normalized = DpiProxyTestCatalog.normalizeCommand(raw)
        if (normalized.isEmpty() || !DpiArgs.isValid(normalized)) return null
        val args = DpiArgs.resolve(DpiPreset.CUSTOM, normalized)
        if (args.isEmpty()) return null
        return runCatching {
            DpiProxyTestStrategy(
                id = CUSTOM_STRATEGY_ID,
                referenceIndex = Int.MAX_VALUE,
                command = normalized,
                args = args,
            )
        }.getOrNull()
    }

    fun build(referenceIds: Set<String>, customRaw: String): List<DpiProxyTestStrategy> {
        val selected = DpiProxyTestCatalog.strategies.filter { it.id in referenceIds }.toMutableList()
        if (customRaw.isNotBlank()) {
            custom(customRaw)?.let(selected::add)
        }
        return selected
    }

    fun isCustom(strategy: DpiProxyTestStrategy): Boolean = strategy.id == CUSTOM_STRATEGY_ID
}

/** One ordering for live results and reopened history. */
fun rankDpiSummaries(results: List<DpiProxyTestResultSummary>): List<DpiProxyTestResultSummary> =
    results.sortedWith(
        compareByDescending<DpiProxyTestResultSummary> { it.backendStarted && it.completed }
            .thenByDescending { it.fullCoverage }
            .thenByDescending { it.fullyWorkingHosts }
            .thenByDescending { it.totalSuccesses }
            .thenBy { it.medianLatencyMs ?: Long.MAX_VALUE }
            .thenBy { it.strategy.referenceIndex },
    )

fun DpiProxyTestRun.bestCandidate(): DpiProxyTestResultSummary? =
    rankDpiSummaries(results).firstOrNull { it.canApply }

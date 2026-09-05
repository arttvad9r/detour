package dev.triplet.app.core

/** One host scope assigned to the strategy that passed all of its probe endpoints. */
data class DpiScopeStrategyAssignment(
    val scopeHost: String,
    val targets: List<DpiProbeTarget>,
    val candidate: DpiStrategyCandidate,
) {
    init {
        require(scopeHost.isNotBlank()) { "scope host is blank" }
        require(targets.isNotEmpty()) { "scope has no probe targets" }
        require(targets.all { it.scopeHost == scopeHost }) { "probe target belongs to another scope" }
    }
}

/**
 * Per-domain result derived from an exhaustive candidate x affected-endpoint matrix.
 * A scope gets a rule only when one candidate fully passes every selected probe
 * endpoint that the broad rule would affect.
 */
data class DpiPerDomainPlan(
    val directTargets: List<DpiProbeTarget>,
    val assignments: List<DpiScopeStrategyAssignment>,
    val unresolvedScopeHosts: List<String>,
) {
    val complete: Boolean get() = unresolvedScopeHosts.isEmpty()
}

/**
 * Picks the best fully-working candidate independently for every affected rule
 * scope. Stability across every probe in the scope comes before latency and
 * strategy complexity.
 */
object DpiPerDomainPlanner {
    fun fromReport(report: DpiAutoSearchReport): DpiPerDomainPlan {
        val baselineByScope = report.baseline.groupBy { it.target.scopeHost }
        val affectedScopes = baselineByScope
            .filterValues { results -> results.any { !it.fullyWorking } }
            .keys
        val direct = report.baseline
            .filter { it.target.scopeHost !in affectedScopes }
            .map { it.target }
        val assignments = mutableListOf<DpiScopeStrategyAssignment>()
        val unresolved = mutableListOf<String>()

        for (scopeHost in affectedScopes.sorted()) {
            val scopeTargets = baselineByScope.getValue(scopeHost).map { it.target }
            val targetIds = scopeTargets.map { it.id }.toSet()
            val best = report.strategies
                .asSequence()
                .filter { it.backendStarted }
                .mapNotNull { strategy ->
                    val results = strategy.targets.filter { it.target.id in targetIds }
                    if (results.size != scopeTargets.size) return@mapNotNull null
                    val byId = results.associateBy { it.target.id }
                    if (scopeTargets.any { expected ->
                            val actual = byId[expected.id]
                            actual == null || actual.target.host != expected.host ||
                                actual.target.scopeHost != scopeHost || !actual.fullyWorking
                        }
                    ) return@mapNotNull null
                    val attempts = results.sumOf { it.attempts }
                    val latencies = results.flatMap { it.successfulLatenciesMs }.sorted()
                    val median = latencies.takeIf { it.isNotEmpty() }
                        ?.let { it[(it.size - 1) / 2] }
                    ScopeCandidate(strategy, attempts, median)
                }
                .sortedWith(
                    compareByDescending<ScopeCandidate> { it.attempts }
                        .thenBy { it.medianLatencyMs ?: Long.MAX_VALUE }
                        .thenBy { it.strategy.candidate.complexity }
                        .thenBy { it.strategy.candidate.id },
                )
                .firstOrNull()

            if (best == null) {
                unresolved += scopeHost
            } else {
                assignments += DpiScopeStrategyAssignment(
                    scopeHost = scopeHost,
                    targets = scopeTargets,
                    candidate = best.strategy.candidate,
                )
            }
        }

        return DpiPerDomainPlan(
            directTargets = direct,
            assignments = assignments,
            unresolvedScopeHosts = unresolved,
        )
    }

    private data class ScopeCandidate(
        val strategy: DpiStrategyResult,
        val attempts: Int,
        val medianLatencyMs: Long?,
    )
}

/** A concrete host-restricted ByeDPI group emitted by the compiler. */
data class DpiHostStrategyGroup(
    val candidate: DpiStrategyCandidate,
    val hosts: List<String>,
)

/** Structured compiler output retained for diagnostics and persistence tests. */
data class DpiCompiledDomainPlan(
    val args: List<String>,
    val groups: List<DpiHostStrategyGroup>,
)

/**
 * Compiles a complete per-domain plan to ByeDPI v0.17.3 argv.
 *
 * Pinned ByeDPI semantics:
 * - `-H :hosts` limits the current desync group by TLS/HTTP hostname;
 * - `-A n` starts the next group without a trigger requirement;
 * - when every explicit group is limited, ByeDPI appends an empty fallback
 *   group, so traffic outside the selected hosts remains unmodified.
 *
 * Current AUTO candidates intentionally do not contain their own `-A` groups.
 * Supporting nested/multi-group candidates requires a richer strategy IR and
 * is rejected here rather than flattened incorrectly.
 */
object DpiPerDomainCommandCompiler {
    // v0.17.3 stores group bits in uint64_t but constructs them with `1 << id`
    // from a signed int literal. Keeping the fallback group at id <= 30 avoids
    // relying on undefined signed shifts at id 31+ in the pinned C source.
    const val MAX_EXPLICIT_GROUPS = 30

    private val optionArity = mapOf(
        "-s" to 1,
        "--split" to 1,
        "-d" to 1,
        "--disorder" to 1,
        "-o" to 1,
        "--oob" to 1,
        "-q" to 1,
        "--disoob" to 1,
        "-r" to 1,
        "--tlsrec" to 1,
        "-a" to 1,
        "--udp-fake" to 1,
    )

    private val forbiddenControls = setOf(
        "-A", "--auto", "-H", "--hosts",
        "-i", "--ip", "-p", "--port", "-D", "--daemon", "-w", "--pidfile",
        "-E", "--transparent", "-U", "--no-udp", "-J",
    )

    private data class PreparedCandidate(
        val candidate: DpiStrategyCandidate,
        val groupArgs: List<String>,
        val timeout: String?,
    )

    fun compile(plan: DpiPerDomainPlan): DpiCompiledDomainPlan {
        require(plan.complete) { "cannot compile unresolved per-domain scopes" }
        require(plan.assignments.isNotEmpty()) { "per-domain plan has no DPI assignments" }

        val normalizedAssignments = plan.assignments.map { assignment ->
            val normalizedScope = normalizeHost(assignment.scopeHost)
            require(assignment.targets.all { normalizeHost(it.scopeHost) == normalizedScope }) {
                "probe target belongs to another normalized scope"
            }
            assignment.copy(scopeHost = normalizedScope)
        }
        val byScope = normalizedAssignments.groupBy { it.scopeHost }
        require(byScope.values.all { assignments ->
            assignments.map { it.candidate.id }.distinct().size == 1
        }) { "same scope assigned to multiple strategies" }
        val uniqueAssignments = byScope.values.map { it.first() }

        val candidateDefinitions = uniqueAssignments
            .groupBy { it.candidate.id }
            .mapValues { (_, values) -> values.map { it.candidate }.distinct() }
        require(candidateDefinitions.values.all { it.size == 1 }) {
            "candidate id maps to multiple argument sets"
        }

        val prepared = candidateDefinitions.mapValues { (_, definitions) ->
            prepare(definitions.single())
        }
        val timeouts = prepared.values.map { it.timeout }.distinct()
        require(timeouts.size == 1) {
            "per-domain candidates use incompatible global timeouts"
        }

        val grouped = uniqueAssignments
            .groupBy { it.candidate.id }
            .map { (candidateId, assignments) ->
                DpiHostStrategyGroup(
                    candidate = prepared.getValue(candidateId).candidate,
                    hosts = assignments.map { it.scopeHost }.distinct().sorted(),
                )
            }

        val groups = orderGroups(grouped) ?: orderGroups(
            uniqueAssignments.map { assignment ->
                DpiHostStrategyGroup(assignment.candidate, listOf(assignment.scopeHost))
            },
        ) ?: error("host precedence graph is cyclic")

        require(groups.size <= MAX_EXPLICIT_GROUPS) {
            "too many explicit ByeDPI groups: ${groups.size}"
        }

        val args = mutableListOf<String>()
        prepared.values.first().timeout?.let { timeout ->
            args += "--timeout"
            args += timeout
        }
        groups.forEachIndexed { index, group ->
            args += "-H"
            args += ":${group.hosts.joinToString(" ")}"
            args += prepared.getValue(group.candidate.id).groupArgs
            if (index != groups.lastIndex) {
                args += "-A"
                args += "n"
            }
        }

        return DpiCompiledDomainPlan(args = args, groups = groups)
    }

    private fun prepare(candidate: DpiStrategyCandidate): PreparedCandidate {
        val groupArgs = mutableListOf<String>()
        var timeout: String? = null
        var index = 0
        while (index < candidate.args.size) {
            val option = candidate.args[index]
            require(option !in forbiddenControls &&
                !option.startsWith("--auto=") && !option.startsWith("--hosts=")) {
                "candidate ${candidate.id} contains unsupported group control: $option"
            }

            when {
                option == "--timeout" || option == "-T" -> {
                    require(index + 1 < candidate.args.size) { "missing timeout value" }
                    val value = candidate.args[index + 1]
                    require(value.isNotBlank() && !value.startsWith('-')) { "invalid timeout value" }
                    require(timeout == null || timeout == value) { "candidate has multiple timeouts" }
                    timeout = value
                    index += 2
                }
                option.startsWith("--timeout=") -> {
                    val value = option.substringAfter('=')
                    require(value.isNotBlank()) { "invalid timeout value" }
                    require(timeout == null || timeout == value) { "candidate has multiple timeouts" }
                    timeout = value
                    index++
                }
                else -> {
                    val arity = optionArity[option]
                        ?: error("candidate ${candidate.id} uses unsupported per-domain option: $option")
                    require(index + arity < candidate.args.size) { "missing value for $option" }
                    groupArgs += option
                    repeat(arity) { offset -> groupArgs += candidate.args[index + 1 + offset] }
                    index += arity + 1
                }
            }
        }
        require(groupArgs.isNotEmpty()) { "candidate ${candidate.id} has no group-local strategy" }
        return PreparedCandidate(candidate, groupArgs, timeout)
    }

    private fun normalizeHost(host: String): String {
        val parsed = DpiDomainInput.parse(host)
        require(parsed.invalid.isEmpty() && parsed.targets.size == 1) { "invalid host in plan: $host" }
        return parsed.targets.single().host
    }

    /**
     * More-specific host groups must precede ancestor host groups because
     * ByeDPI host matching includes subdomains. Returns null if grouping by
     * candidate creates contradictory precedence; the caller then splits to
     * singleton host groups, which always has an acyclic suffix ordering.
     */
    private fun orderGroups(groups: List<DpiHostStrategyGroup>): List<DpiHostStrategyGroup>? {
        if (groups.isEmpty()) return emptyList()
        val outgoing = groups.indices.associateWith { mutableSetOf<Int>() }.toMutableMap()
        val incoming = IntArray(groups.size)

        for (childIndex in groups.indices) {
            for (parentIndex in groups.indices) {
                if (childIndex == parentIndex) continue
                val mustPrecede = groups[childIndex].hosts.any { child ->
                    groups[parentIndex].hosts.any { parent -> isStrictSubdomain(child, parent) }
                }
                if (mustPrecede && outgoing.getValue(childIndex).add(parentIndex)) {
                    incoming[parentIndex]++
                }
            }
        }

        val available = groups.indices.filter { incoming[it] == 0 }.toMutableList()
        val ordered = mutableListOf<DpiHostStrategyGroup>()
        while (available.isNotEmpty()) {
            available.sortWith(
                compareByDescending<Int> { index -> groups[index].hosts.maxOf { host -> hostDepth(host) } }
                    .thenBy { index -> groups[index].candidate.id }
                    .thenBy { index -> groups[index].hosts.joinToString("\u0000") },
            )
            val index = available.removeAt(0)
            ordered += groups[index]
            outgoing.getValue(index).sorted().forEach { next ->
                incoming[next]--
                if (incoming[next] == 0) available += next
            }
        }

        return ordered.takeIf { it.size == groups.size }
    }

    private fun isStrictSubdomain(child: String, parent: String): Boolean =
        child.length > parent.length && child.endsWith(".$parent")

    private fun hostDepth(host: String): Int = host.count { it == '.' } + 1
}

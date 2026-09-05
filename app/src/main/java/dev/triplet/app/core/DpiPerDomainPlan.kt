package dev.triplet.app.core

/** One tested host assigned to the strategy that worked best for it. */
data class DpiTargetStrategyAssignment(
    val target: DpiProbeTarget,
    val candidate: DpiStrategyCandidate,
)

/**
 * Per-domain result derived from an exhaustive candidate x target test matrix.
 * Directly reachable targets intentionally have no ByeDPI rule.
 */
data class DpiPerDomainPlan(
    val directTargets: List<DpiProbeTarget>,
    val assignments: List<DpiTargetStrategyAssignment>,
    val unresolvedTargets: List<DpiProbeTarget>,
) {
    val complete: Boolean get() = unresolvedTargets.isEmpty()
}

/**
 * Picks the best fully-working candidate independently for every target that
 * failed the direct baseline. More repeated observations win before latency;
 * latency and strategy complexity are only tie breakers.
 */
object DpiPerDomainPlanner {
    fun fromReport(report: DpiAutoSearchReport): DpiPerDomainPlan {
        val direct = report.baseline.filter { it.fullyWorking }.map { it.target }
        val problematic = report.baseline.filterNot { it.fullyWorking }.map { it.target }
        val assignments = mutableListOf<DpiTargetStrategyAssignment>()
        val unresolved = mutableListOf<DpiProbeTarget>()

        for (target in problematic) {
            val best = report.strategies
                .asSequence()
                .filter { it.backendStarted }
                .mapNotNull { strategy ->
                    val targetResult = strategy.targets.firstOrNull { it.target.id == target.id }
                        ?: return@mapNotNull null
                    if (!targetResult.fullyWorking || targetResult.target.host != target.host) {
                        return@mapNotNull null
                    }
                    strategy to targetResult
                }
                .sortedWith(
                    compareByDescending<Pair<DpiStrategyResult, DpiTargetResult>> { it.second.attempts }
                        .thenBy { it.second.medianLatencyMs ?: Long.MAX_VALUE }
                        .thenBy { it.first.candidate.complexity }
                        .thenBy { it.first.candidate.id },
                )
                .firstOrNull()

            if (best == null) {
                unresolved += target
            } else {
                assignments += DpiTargetStrategyAssignment(target, best.first.candidate)
            }
        }

        return DpiPerDomainPlan(
            directTargets = direct,
            assignments = assignments,
            unresolvedTargets = unresolved,
        )
    }
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
        require(plan.complete) { "cannot compile unresolved per-domain targets" }
        require(plan.assignments.isNotEmpty()) { "per-domain plan has no DPI assignments" }

        val byTargetId = plan.assignments.groupBy { it.target.id }
        require(byTargetId.values.all { it.size == 1 }) { "duplicate target id assignment" }

        val normalized = plan.assignments.map { assignment ->
            DpiTargetStrategyAssignment(
                target = assignment.target.copy(host = normalizeHost(assignment.target.host)),
                candidate = assignment.candidate,
            )
        }
        val byHost = normalized.groupBy { it.target.host }
        require(byHost.values.all { assignments ->
            assignments.map { it.candidate.id }.distinct().size == 1
        }) { "same host assigned to multiple strategies" }

        val normalizedAssignments = byHost.values.map { sameHost -> sameHost.first() }
        val candidateDefinitions = normalizedAssignments
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

        val grouped = normalizedAssignments
            .groupBy { it.candidate.id }
            .map { (candidateId, assignments) ->
                DpiHostStrategyGroup(
                    candidate = prepared.getValue(candidateId).candidate,
                    hosts = assignments.map { it.target.host }.distinct().sorted(),
                )
            }

        val groups = orderGroups(grouped) ?: orderGroups(
            normalizedAssignments.map { assignment ->
                DpiHostStrategyGroup(assignment.candidate, listOf(assignment.target.host))
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

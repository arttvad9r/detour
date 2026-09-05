package dev.triplet.app.core

import org.json.JSONObject

/**
 * Persisted representation of per-domain AUTO selection.
 * Only normalized host scopes and trusted catalog IDs are stored; raw ByeDPI
 * arguments are always regenerated from the current app-owned catalog.
 */
class DpiAutoDomainPlan private constructor(
    val scopeCandidates: Map<String, String>,
) {
    init {
        require(scopeCandidates.isNotEmpty()) { "domain AUTO plan is empty" }
        require(scopeCandidates.size <= DpiDomainInput.MAX_DOMAINS) {
            "too many domain AUTO scopes"
        }
        scopeCandidates.forEach { (scope, candidateId) ->
            require(normalizeScope(scope) == scope) { "domain AUTO scope is not normalized" }
            require(DpiStrategyCatalog.byId(candidateId) != null) {
                "unknown automatic DPI strategy: $candidateId"
            }
        }
    }

    fun compileArgs(): List<String> {
        val assignments = scopeCandidates.entries.map { (scope, candidateId) ->
            val candidate = requireNotNull(DpiStrategyCatalog.byId(candidateId))
            val target = DpiProbeTarget(
                id = "persisted:$scope",
                host = scope,
                scopeHost = scope,
            )
            DpiScopeStrategyAssignment(
                scopeHost = scope,
                targets = listOf(target),
                candidate = candidate,
            )
        }
        return DpiPerDomainCommandCompiler.compile(
            DpiPerDomainPlan(
                directTargets = emptyList(),
                assignments = assignments,
                unresolvedScopeHosts = emptyList(),
            ),
        ).args
    }

    fun toStored(): String = JSONObject().apply {
        put("v", VERSION)
        put("scopes", JSONObject(scopeCandidates))
    }.toString()

    override fun equals(other: Any?): Boolean =
        other is DpiAutoDomainPlan && scopeCandidates == other.scopeCandidates

    override fun hashCode(): Int = scopeCandidates.hashCode()

    override fun toString(): String = "DpiAutoDomainPlan(scopes=${scopeCandidates.size})"

    companion object {
        const val VERSION = 1
        private const val MAX_STORED_BYTES = 16 * 1024

        fun fromPlan(plan: DpiPerDomainPlan): DpiAutoDomainPlan {
            require(plan.complete) { "cannot persist unresolved per-domain scopes" }
            return of(plan.assignments.associate { it.scopeHost to it.candidate.id })
                .also { it.compileArgs() }
        }

        fun of(scopeCandidates: Map<String, String>): DpiAutoDomainPlan {
            require(scopeCandidates.isNotEmpty()) { "domain AUTO plan is empty" }
            val normalized = linkedMapOf<String, String>()
            scopeCandidates.entries.sortedBy { it.key }.forEach { (rawScope, candidateId) ->
                val scope = normalizeScope(rawScope)
                val previous = normalized.putIfAbsent(scope, candidateId)
                require(previous == null || previous == candidateId) {
                    "same normalized scope maps to multiple strategies"
                }
            }
            return DpiAutoDomainPlan(normalized.toMap())
        }

        fun fromStored(raw: String): DpiAutoDomainPlan? = try {
            if (raw.isBlank() || raw.toByteArray(Charsets.UTF_8).size > MAX_STORED_BYTES) return null
            val root = JSONObject(raw)
            if (root.optInt("v", -1) != VERSION) return null
            val scopes = root.optJSONObject("scopes") ?: return null
            val parsed = linkedMapOf<String, String>()
            scopes.keys().forEach { scope ->
                val candidateId = scopes.optString(scope)
                if (candidateId.isBlank()) return null
                parsed[scope] = candidateId
            }
            of(parsed).also { it.compileArgs() }
        } catch (_: Exception) {
            null
        }

        private fun normalizeScope(raw: String): String {
            val parsed = DpiDomainInput.parse(raw)
            require(parsed.invalid.isEmpty() && parsed.targets.size == 1) {
                "invalid domain AUTO scope: $raw"
            }
            return parsed.targets.single().host
        }
    }
}

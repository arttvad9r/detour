package dev.detour.app.core

import org.json.JSONArray
import org.json.JSONObject

enum class DpiTestOutcome { COMPLETED, CANCELLED, VPN_ACTIVE, FAILED }

/** Snapshot of one attempt, independent of subsequent UI selections or older history. */
data class DpiProxyTestReport(
    val selectedDomainIds: Set<String>,
    val config: DpiProxyTestConfig,
    val outcome: DpiTestOutcome,
    val results: List<DpiProxyTestResultSummary> = emptyList(),
) {
    companion object {
        fun fromRun(run: DpiProxyTestRun) = DpiProxyTestReport(
            run.selectedDomainIds, run.config, DpiTestOutcome.COMPLETED, run.results,
        )
    }

    /** Construct from a fixed allowlist; never serialize settings, commands, hosts or raw errors. */
    fun toJson(appVersion: String? = null, androidApi: Int? = null): String = JSONObject()
        .put("schema", 1)
        .put("app", "Detour")
        .apply {
            appVersion?.let { put("appVersion", it) }
            androidApi?.let { put("androidApi", it) }
        }
        .put("probe", "authenticated-socks-https")
        .put("outcome", outcome.name.lowercase())
        .put("attemptsPerHost", config.attemptsPerHost)
        .put("concurrency", config.concurrency)
        .put("timeoutSeconds", config.timeoutSeconds)
        .put("services", JSONArray().apply {
            DpiProxyTestCatalog.domainLists.filter { it.id in selectedDomainIds }.forEach { put(it.id) }
        })
        .put("results", JSONArray().apply {
            rankDpiSummaries(results).forEach { result ->
                val reference = DpiProxyTestCatalog.strategies.firstOrNull { it == result.strategy }
                put(JSONObject()
                    .put("strategy", reference?.referenceIndex ?: "custom")
                    .put("backendStarted", result.backendStarted)
                    .put("completed", result.completed)
                    .put("hosts", result.hostCount)
                    .put("fullyWorkingHosts", result.fullyWorkingHosts)
                    .put("successes", result.totalSuccesses)
                    .put("attempts", result.totalAttempts)
                    .apply { result.medianLatencyMs?.let { put("medianLatencyMs", it) } }
                    .put("services", JSONArray().apply {
                        result.services.filter { service ->
                            DpiProxyTestCatalog.domainLists.any { it.id == service.serviceId } &&
                                service.serviceId in selectedDomainIds
                        }.forEach { service -> put(JSONObject()
                            .put("id", service.serviceId).put("hosts", service.hostCount)
                            .put("fullyWorkingHosts", service.fullyWorkingHosts)
                            .put("successes", service.successes).put("attempts", service.attempts)) }
                    }))
            }
        }).toString(2)
}

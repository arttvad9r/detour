package dev.detour.app.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DpiTestResultsTest {
    private val reference = DpiProxyTestCatalog.strategies.first()
    private fun summary(successes: Int = 3, working: Int = 1) = DpiProxyTestResultSummary(
        reference, true, true, 1, working, successes, 3, if (successes > 0) 12 else null,
    )
    private fun run(results: List<DpiProxyTestResultSummary>) = DpiProxyTestRun(
        "private-run-id", 1_700_000_000_000, setOf("youtube"), DpiProxyTestConfig(), results,
    )

    @Test fun `defaults repeat checks rather than claiming stability after one attempt`() {
        assertEquals(3, DpiProxyTestConfig().attemptsPerHost)
    }

    @Test fun `no success incomplete and failed backend results cannot be applied or recommended`() {
        for (result in listOf(summary(0, 0), summary().copy(completed = false), summary().copy(backendStarted = false))) {
            assertFalse(result.canApply)
            assertNull(run(listOf(result)).bestCandidate())
        }
    }

    @Test fun `a partial candidate is eligible but cannot claim full coverage`() {
        val partial = summary(1, 0)
        assertTrue(partial.canApply)
        assertFalse(partial.fullCoverage)
        assertEquals(partial, run(listOf(partial)).bestCandidate())
    }

    @Test fun `reopened unsorted results prefer repeated coverage to speed`() {
        val slowFull = summary().copy(strategy = DpiProxyTestCatalog.strategies[1], medianLatencyMs = 400)
        val fastPartial = summary(1, 0).copy(medianLatencyMs = 2)
        assertEquals(slowFull, run(listOf(fastPartial, slowFull)).bestCandidate())
    }

    @Test fun `group results only use the selected services and actual checked hosts`() {
        val raw = DpiProxyTestStrategyResult(reference, true, true, listOf(
            DpiProxyTestHostResult("youtube.com", 3, 3, 3, listOf(10, 12, 14)),
            DpiProxyTestHostResult("youtu.be", 3, 3, 1, listOf(20)),
        ))
        val grouped = raw.toSummary(setOf("youtube", "discord"))
        assertEquals(listOf(DpiProxyTestServiceSummary("youtube", 2, 1, 4, 6)), grouped.services)
        assertEquals(1, grouped.fullyWorkingHosts)
        assertTrue(raw.toSummary().services.isEmpty())
    }

    @Test fun `report includes only whitelisted identifiers and numeric aggregates`() {
        val secret = "private-token.example"
        val custom = requireNotNull(DpiProxyTestStrategySelection.custom("-n $secret -d1"))
        val malicious = summary().copy(strategy = custom, services = listOf(
            DpiProxyTestServiceSummary(secret, 1, 1, 3, 3),
            DpiProxyTestServiceSummary("youtube", 1, 1, 3, 3),
        ))
        val payload = DpiProxyTestReport(setOf("youtube", secret), DpiProxyTestConfig(),
            DpiTestOutcome.COMPLETED, listOf(malicious)).toJson()
        assertFalse(payload.contains(secret))
        assertFalse(payload.contains(custom.command))
        assertFalse(payload.contains("private-run-id"))
        val json = JSONObject(payload)
        assertEquals("custom", json.getJSONArray("results").getJSONObject(0).getString("strategy"))
        assertEquals(listOf("youtube"), json.getJSONArray("services").let { a -> (0 until a.length()).map(a::getString) })
        assertEquals(1, json.getJSONArray("results").getJSONObject(0).getJSONArray("services").length())
    }

    @Test fun `forged reference IDs do not disclose arbitrary strategy fields`() {
        val forged = reference.copy(command = "-n private.example -d1", args = listOf("-n", "private.example", "-d1"))
        val payload = DpiProxyTestReport.fromRun(run(listOf(summary().copy(strategy = forged)))).toJson()
        assertFalse(payload.contains("private.example"))
        assertEquals("custom", JSONObject(payload).getJSONArray("results").getJSONObject(0).getString("strategy"))
        val trusted = DpiProxyTestReport.fromRun(run(listOf(summary()))).toJson()
        assertEquals(reference.referenceIndex, JSONObject(trusted).getJSONArray("results").getJSONObject(0).getInt("strategy"))
    }

    @Test fun `failure and cancellation reports cannot accidentally include an older run`() {
        for (outcome in listOf(DpiTestOutcome.FAILED, DpiTestOutcome.CANCELLED, DpiTestOutcome.VPN_ACTIVE)) {
            val report = DpiProxyTestReport(setOf("youtube"), DpiProxyTestConfig(), outcome)
            val json = JSONObject(report.toJson())
            assertEquals(outcome.name.lowercase(), json.getString("outcome"))
            assertEquals(0, json.getJSONArray("results").length())
        }
    }
}

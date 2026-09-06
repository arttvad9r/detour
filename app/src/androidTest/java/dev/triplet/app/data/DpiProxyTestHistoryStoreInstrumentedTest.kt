package dev.triplet.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.triplet.app.core.DpiProxyTestCatalog
import dev.triplet.app.core.DpiProxyTestConfig
import dev.triplet.app.core.DpiProxyTestResultSummary
import dev.triplet.app.core.DpiProxyTestRun
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DpiProxyTestHistoryStoreInstrumentedTest {
    @Test fun completedRunSurvivesStoreRecreation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val strategy = DpiProxyTestCatalog.strategies.first()
        val run = DpiProxyTestRun(
            id = "instrumented-history-run",
            createdAtEpochMs = 1_700_000_000_000,
            selectedDomainIds = setOf("youtube"),
            config = DpiProxyTestConfig(),
            results = listOf(
                DpiProxyTestResultSummary(
                    strategy = strategy,
                    backendStarted = true,
                    completed = true,
                    hostCount = 13,
                    fullyWorkingHosts = 12,
                    totalSuccesses = 12,
                    totalAttempts = 13,
                    medianLatencyMs = 31,
                ),
            ),
        )

        val firstStore = DpiProxyTestHistoryStore(context)
        try {
            firstStore.save(listOf(run))
            val recreatedStore = DpiProxyTestHistoryStore(context)
            assertEquals(listOf(run), recreatedStore.load())
        } finally {
            firstStore.save(emptyList())
        }
    }
}

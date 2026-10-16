package dev.detour.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.detour.app.core.*
import dev.detour.app.data.DpiProxyTestHistoryStore
import dev.detour.app.data.RoutesMapping
import dev.detour.app.data.TriSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DpiStrategyApplyTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun exercise(successes: Int = 3, failFirst: Boolean = false, body: suspend (DpiViewModel, MutableStateFlow<TriSettings?>, () -> Int, () -> Int, File) -> Unit) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "dpi-test-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
        }
        val strategy = DpiProxyTestCatalog.strategies.first()
        val history = DpiProxyTestHistoryStore(isolated)
        history.save(listOf(DpiProxyTestRun("fixture", 1000, setOf("youtube"), DpiProxyTestConfig(), listOf(
            DpiProxyTestResultSummary(strategy, true, true, 1, if (successes == 3) 1 else 0,
                successes, 3, if (successes > 0) 10 else null),
        ))))
        val settings = MutableStateFlow<TriSettings?>(RoutesMapping.toSettings(emptyMap()))
        var writes = 0
        var restarts = 0
        val owner = ViewModelStore()
        lateinit var vm: DpiViewModel
        instrumentation.runOnMainSync {
            vm = DpiViewModel(settings, {}, {}, { restarts++ }, SavedStateHandle(), history,
                saveTestedStrategy = { command ->
                    writes++
                    if (failFirst && writes == 1) error("Synthetic write failure")
                    settings.value = requireNotNull(settings.value).copy(preset = DpiPreset.CUSTOM, dpiCustomArgs = command)
                })
            owner.put("dpi", vm)
        }
        try {
            withTimeout(10_000) { vm.proxyTestState.first { it.historyLoaded } }
            body(vm, settings, { writes }, { restarts }, directory)
        } finally {
            instrumentation.runOnMainSync { owner.clear() }
            directory.deleteRecursively()
        }
    }

    @Test fun bestStrategyIsSavedOnceBeforeOneRestart() = exercise { vm, settings, writes, restarts, _ ->
        val id = requireNotNull(vm.proxyTestState.value.bestCandidate).strategy.id
        instrumentation.runOnMainSync { vm.applyProxyStrategy(id); vm.applyProxyStrategy(id) }
        withTimeout(10_000) { vm.proxyTestState.first { it.appliedStrategyId == id } }
        assertEquals(1, writes())
        assertEquals(1, restarts())
        assertEquals(DpiPreset.CUSTOM, settings.value?.preset)
    }

    @Test fun appliedMarkerFollowsSavedSettingsWhenHistoryIsReopened() = exercise { vm, settings, _, _, _ ->
        val run = requireNotNull(vm.proxyTestState.value.selectedRun)
        val id = requireNotNull(vm.proxyTestState.value.bestCandidate).strategy.id
        instrumentation.runOnMainSync { vm.applyProxyStrategy(id) }
        withTimeout(10_000) { vm.proxyTestState.first { it.appliedStrategyId == id } }
        instrumentation.runOnMainSync { vm.selectProxyRun(run.id) }
        withTimeout(10_000) { vm.proxyTestState.first { it.appliedStrategyId == id } }
        instrumentation.runOnMainSync { settings.value = requireNotNull(settings.value).copy(preset = DpiPreset.RECOMMENDED) }
        withTimeout(10_000) { vm.proxyTestState.first { it.appliedStrategyId == null } }
        instrumentation.runOnMainSync { settings.value = requireNotNull(settings.value).copy(preset = DpiPreset.CUSTOM) }
        withTimeout(10_000) { vm.proxyTestState.first { it.appliedStrategyId == id } }
    }

    @Test fun zeroSuccessCannotBeAppliedEvenWhenCalledDirectly() = exercise(successes = 0) { vm, _, writes, restarts, _ ->
        val id = vm.proxyTestState.value.results.single().strategy.id
        instrumentation.runOnMainSync { vm.applyProxyStrategy(id) }
        assertEquals(0, writes())
        assertEquals(0, restarts())
    }

    @Test fun failedSaveDoesNotRestartAndCanBeRetried() = exercise(failFirst = true) { vm, _, writes, restarts, _ ->
        val id = requireNotNull(vm.proxyTestState.value.bestCandidate).strategy.id
        instrumentation.runOnMainSync { vm.applyProxyStrategy(id) }
        withTimeout(10_000) { vm.proxyTestState.first { it.applyErrorStrategyId == id } }
        assertEquals(0, restarts())
        instrumentation.runOnMainSync { vm.applyProxyStrategy(id) }
        withTimeout(10_000) { vm.proxyTestState.first { it.appliedStrategyId == id } }
        assertEquals(2, writes())
        assertEquals(1, restarts())
    }

    @Test fun reportWritePreservesCapturedPayload() = exercise { vm, _, _, _, directory ->
        val report = DpiProxyTestReport(setOf("youtube"), DpiProxyTestConfig(), DpiTestOutcome.CANCELLED).toJson()
        val file = File(directory, "report.json")
        instrumentation.runOnMainSync { vm.saveDiagnostic(ApplicationProvider.getApplicationContext(), Uri.fromFile(file), report) }
        withTimeout(10_000) { vm.proxyTestState.first { it.reportSaveState == DpiReportSaveState.SAVED || it.reportSaveState == DpiReportSaveState.ERROR } }
        assertEquals(DpiReportSaveState.SAVED, vm.proxyTestState.value.reportSaveState)
        assertEquals(report, file.readText())
    }
}

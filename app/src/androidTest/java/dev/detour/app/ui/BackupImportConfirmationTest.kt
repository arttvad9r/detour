package dev.detour.app.ui

import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import dev.detour.app.core.SettingsBackup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class BackupImportConfirmationTest {
    @Test fun importDoesNotReplaceSettingsOrStopTunnelUntilConfirmed() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val owner = ViewModelStore()
        var restored = 0
        var stops = 0
        var reads = 0
        val backup = SettingsBackup.Backup(themeId = "detour_dark")
        lateinit var vm: BackupViewModel
        instrumentation.runOnMainSync {
            vm = BackupViewModel({ null }, { reads++; SettingsBackup.toJson(backup) }, { _, _ -> },
                { assertEquals(backup, it); restored++ }, { stops++ })
            owner.put("backup", vm)
            vm.importDocument("synthetic://backup")
        }
        try {
            withTimeout(10_000) { vm.pendingImport.first { it != null } }
            assertEquals(0, restored)
            assertEquals(0, stops)
            instrumentation.runOnMainSync { vm.dismissImport() }
            assertNull(vm.pendingImport.value)
            assertNull(vm.operation.value)
            assertEquals(0, restored)
            instrumentation.runOnMainSync { vm.importDocument("synthetic://backup") }
            withTimeout(10_000) { vm.pendingImport.first { it != null } }
            instrumentation.runOnMainSync { vm.confirmImport(); vm.confirmImport() }
            withTimeout(10_000) { vm.status.first { it == BackupStatus.IMPORTED } }
            assertEquals(1, restored)
            assertEquals(1, stops)
            assertEquals(2, reads)
        } finally {
            instrumentation.runOnMainSync { owner.clear() }
        }
    }
}

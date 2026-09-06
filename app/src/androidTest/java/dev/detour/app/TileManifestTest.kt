package dev.detour.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.detour.app.tile.DetourTile
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TileManifestTest {

    @Test fun quickSettingsTileIsDeclaredToggleable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val component = ComponentName(context, DetourTile::class.java)
        val serviceInfo = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getServiceInfo(
                component,
                PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getServiceInfo(component, PackageManager.GET_META_DATA)
        }

        assertTrue(
            serviceInfo.metaData?.getBoolean("android.service.quicksettings.TOGGLEABLE_TILE") == true,
        )
    }
}

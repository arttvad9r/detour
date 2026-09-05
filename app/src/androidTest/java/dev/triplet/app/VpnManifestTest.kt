package dev.triplet.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.triplet.app.vpn.TriVpnService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VpnManifestTest {

    @Test fun vpnServiceSupportsAndroidAlwaysOnMode() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val component = ComponentName(context, TriVpnService::class.java)
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
            serviceInfo.metaData?.getBoolean("android.net.VpnService.SUPPORTS_ALWAYS_ON", false)
                ?: false,
        )
    }

    @Test fun applicationDisablesPlatformBackupAndCleartextTraffic() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val flags = context.applicationInfo.flags

        assertFalse(flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
        assertFalse(flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0)
    }
}

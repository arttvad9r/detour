package dev.triplet.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

object AppInventory {
    @Volatile
    private var cached: List<AppInfo>? = null

    fun load(context: Context): List<AppInfo> = cached ?: synchronized(this) {
        cached ?: query(context.applicationContext).also { cached = it }
    }

    /** Force a fresh PackageManager scan and replace the process-local snapshot. */
    fun refresh(context: Context): List<AppInfo> = synchronized(this) {
        query(context.applicationContext).also { cached = it }
    }

    fun invalidate() {
        cached = null
    }

    private fun query(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val self = context.packageName
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        // Query launcher activities once instead of calling queryIntentActivities()
        // for every installed package. The result can stay cached for other callers,
        // while the route screen explicitly refreshes it on foreground resume.
        return pm.queryIntentActivities(launcher, 0)
            .asSequence()
            .mapNotNull { it.activityInfo?.applicationInfo }
            .filter { it.packageName != self }
            .distinctBy { it.packageName }
            .map {
                AppInfo(
                    packageName = it.packageName,
                    label = pm.getApplicationLabel(it).toString(),
                    isSystem = it.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }
            .toList()
    }
}

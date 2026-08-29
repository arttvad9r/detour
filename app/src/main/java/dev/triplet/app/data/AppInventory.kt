package dev.triplet.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

object AppInventory {
    @Volatile
    private var cached: List<AppInfo>? = null

    @Volatile
    private var dirty: Boolean = false

    /** Latest process-local snapshot, even if a package change marked it stale. */
    fun peek(): List<AppInfo>? = cached

    fun load(context: Context): List<AppInfo> {
        val current = cached
        if (current != null && !dirty) return current
        return synchronized(this) {
            val inside = cached
            if (inside != null && !dirty) inside
            else query(context.applicationContext).also {
                cached = it
                dirty = false
            }
        }
    }

    /** Force a fresh PackageManager scan and replace the process-local snapshot. */
    fun refresh(context: Context): List<AppInfo> = synchronized(this) {
        query(context.applicationContext).also {
            cached = it
            dirty = false
        }
    }

    /** Keep the old snapshot renderable while marking it for background refresh. */
    fun invalidate() {
        dirty = true
    }

    private fun query(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val self = context.packageName
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        // Query launcher activities once instead of calling queryIntentActivities()
        // for every installed package. The result can stay cached for other callers,
        // while the route screen refreshes it after a foreground/package change.
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

package dev.triplet.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object AppInventory {
    @Volatile
    private var cached: List<AppInfo>? = null

    @Volatile
    private var cachedGeneration: Long = -1L

    private val invalidationGeneration = AtomicLong(0L)
    private val iconCache = ConcurrentHashMap<String, Bitmap>()

    /** Latest process-local snapshot, even if a package change marked it stale. */
    fun peek(): List<AppInfo>? = cached

    /** A ready-to-draw app icon when the background warm-up already loaded it. */
    fun peekIcon(packageName: String): Bitmap? = iconCache[packageName]

    fun load(context: Context): List<AppInfo> {
        val generation = invalidationGeneration.get()
        val current = cached
        if (current != null && cachedGeneration == generation) return current

        return synchronized(this) {
            val insideGeneration = invalidationGeneration.get()
            val inside = cached
            if (inside != null && cachedGeneration == insideGeneration) inside
            else queryUntilStable(context.applicationContext)
        }
    }

    /**
     * Warm both metadata and small route-row icons off the main thread. A 48x48
     * ARGB bitmap is only 9 KiB, so caching launcher icons is cheap while avoiding
     * placeholder -> real-icon swaps during the first navigation animation.
     */
    fun warm(context: Context): List<AppInfo> = load(context).also { apps ->
        apps.forEach { loadIcon(context, it.packageName) }
    }

    fun loadIcon(context: Context, packageName: String): Bitmap? {
        iconCache[packageName]?.let { return it }
        val generation = invalidationGeneration.get()
        val icon = runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(48, 48)
        }.getOrNull() ?: return null

        // A package update/removal can cross the PackageManager call above. The
        // current caller may still render that bitmap, but never repopulate the
        // shared cache with data produced before the invalidation.
        if (invalidationGeneration.get() != generation) return icon
        val existing = iconCache.putIfAbsent(packageName, icon)
        if (existing != null) return existing
        if (invalidationGeneration.get() != generation) {
            iconCache.remove(packageName, icon)
        }
        return icon
    }

    /** Force a fresh PackageManager scan and replace the process-local snapshot. */
    fun refresh(context: Context): List<AppInfo> = synchronized(this) {
        queryUntilStable(context.applicationContext)
    }

    /** Keep the old snapshot renderable while marking it for background refresh. */
    fun invalidate(packageName: String? = null) {
        invalidationGeneration.incrementAndGet()
        if (packageName == null) iconCache.clear() else iconCache.remove(packageName)
    }

    /**
     * Package broadcasts can arrive while PackageManager is being scanned. Only
     * publish a snapshot as fresh when no invalidation crossed that scan; otherwise
     * retry against the new package generation instead of losing the broadcast.
     */
    private fun queryUntilStable(context: Context): List<AppInfo> {
        while (true) {
            val generation = invalidationGeneration.get()
            val result = query(context)
            if (invalidationGeneration.get() == generation) {
                cached = result
                cachedGeneration = generation
                return result
            }
        }
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

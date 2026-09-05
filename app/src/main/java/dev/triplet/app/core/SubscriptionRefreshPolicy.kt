package dev.triplet.app.core

import java.util.concurrent.TimeUnit

object SubscriptionRefreshPolicy {
    const val DEFAULT_INTERVAL_HOURS = 24
    const val MIN_PERIODIC_INTERVAL_HOURS = 1

    fun effectiveIntervalHours(
        configuredHours: Int?,
        providerRecommendedHours: Int?,
    ): Int? {
        if (configuredHours == null) return null
        val requested = providerRecommendedHours
            ?.takeIf { it in VlessKeys.MIN_UPDATE_INTERVAL_HOURS..VlessKeys.MAX_UPDATE_INTERVAL_HOURS }
            ?: configuredHours
        return requested.coerceIn(MIN_PERIODIC_INTERVAL_HOURS, VlessKeys.MAX_UPDATE_INTERVAL_HOURS)
    }

    fun isDue(
        lastUpdatedAt: Long?,
        intervalHours: Int,
        nowMillis: Long,
    ): Boolean {
        require(intervalHours in MIN_PERIODIC_INTERVAL_HOURS..VlessKeys.MAX_UPDATE_INTERVAL_HOURS)
        if (lastUpdatedAt == null || lastUpdatedAt <= 0L) return true
        if (nowMillis <= lastUpdatedAt) return false
        val intervalMillis = TimeUnit.HOURS.toMillis(intervalHours.toLong())
        return nowMillis - lastUpdatedAt >= intervalMillis
    }
}

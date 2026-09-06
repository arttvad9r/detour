package dev.detour.app.core

import java.util.concurrent.TimeUnit

object SubscriptionRefreshPolicy {
    const val DEFAULT_INTERVAL_HOURS = 24
    const val MIN_PERIODIC_INTERVAL_HOURS = 1

    /** Suggested value when enabling refresh; provider metadata wins only at that explicit user action. */
    fun suggestedIntervalHours(providerRecommendedHours: Int?): Int =
        providerRecommendedHours
            ?.takeIf { it in VlessKeys.MIN_UPDATE_INTERVAL_HOURS..VlessKeys.MAX_UPDATE_INTERVAL_HOURS }
            ?: DEFAULT_INTERVAL_HOURS

    /** Null means disabled. Stored values are the user's actual chosen interval. */
    fun effectiveIntervalHours(configuredHours: Int?): Int? =
        configuredHours?.coerceIn(MIN_PERIODIC_INTERVAL_HOURS, VlessKeys.MAX_UPDATE_INTERVAL_HOURS)

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

package dev.triplet.app.core

/**
 * Production hook that normalizes remote subscription bodies before ConfigGenerator
 * hands them to mihomo. Unit tests can keep ConfigGenerator pure by leaving it unset.
 */
object SubscriptionProviderMaterializer {
    @Volatile
    private var handler: ((String) -> String)? = null

    fun install(materializer: (String) -> String) {
        handler = materializer
    }

    fun localPath(subscriptionUrl: String): String? = handler?.invoke(subscriptionUrl)
}

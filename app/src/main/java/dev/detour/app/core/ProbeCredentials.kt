package dev.detour.app.core

import java.security.SecureRandom
import java.util.Base64

/** Process-ephemeral credentials for internal loopback health-probe listeners. */
class ProbeCredentials(
    val username: String,
    val password: String,
) {
    override fun toString(): String = "ProbeCredentials(username=$username, password=<redacted>)"
}

object ProbeAuth {
    private val current = ProbeCredentials(
        username = "detour-probe",
        password = randomToken(),
    )

    fun current(): ProbeCredentials = current

    private fun randomToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

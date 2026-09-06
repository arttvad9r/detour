package dev.detour.app.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationRulesBackupTest {
    private fun rule(type: DestinationRuleType, value: String, route: AppRoute) =
        requireNotNull(DestinationRules.create(type, value, route))

    @Test fun `v4 backup round trips destination rules`() {
        val rules = listOf(
            rule(DestinationRuleType.DOMAIN, "api.example.com", AppRoute.DIRECT),
            rule(DestinationRuleType.DOMAIN_SUFFIX, "example.org", AppRoute.VPN),
            rule(DestinationRuleType.IP_CIDR, "203.0.113.0/24", AppRoute.DPI),
        )

        val restored = SettingsBackup.fromJson(
            SettingsBackup.toJson(SettingsBackup.Backup(destinationRules = rules)),
        )

        assertEquals(rules, restored?.destinationRules)
    }

    @Test fun `v3 backup imports with no destination rules`() {
        val current = JSONObject(SettingsBackup.toJson(SettingsBackup.Backup()))
        current.put("v", 3)
        current.remove("destinationRules")

        val restored = SettingsBackup.fromJson(current.toString())

        assertTrue(restored?.destinationRules?.isEmpty() == true)
    }

    @Test fun `v4 backup rejects invalid destination rule`() {
        val current = JSONObject(SettingsBackup.toJson(SettingsBackup.Backup()))
        current.put(
            "destinationRules",
            org.json.JSONArray(
                """[{"type":"DOMAIN","value":"bad domain","route":"VPN"}]""",
            ),
        )

        assertNull(SettingsBackup.fromJson(current.toString()))
    }
}

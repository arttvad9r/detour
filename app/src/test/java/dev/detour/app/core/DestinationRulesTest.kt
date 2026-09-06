package dev.detour.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationRulesTest {
    @Test fun `domain rules normalize case dots and idn`() {
        assertEquals(
            DestinationRule(DestinationRuleType.DOMAIN, "example.com", AppRoute.VPN),
            DestinationRules.create(DestinationRuleType.DOMAIN, "Example.COM.", AppRoute.VPN),
        )
        assertEquals(
            "xn--e1afmkfd.xn--p1ai",
            DestinationRules.create(
                DestinationRuleType.DOMAIN_SUFFIX,
                ".пример.рф",
                AppRoute.DIRECT,
            )?.value,
        )
    }

    @Test fun `domain rules reject urls wildcards and malformed labels`() {
        assertNull(DestinationRules.create(DestinationRuleType.DOMAIN, "https://example.com", AppRoute.VPN))
        assertNull(DestinationRules.create(DestinationRuleType.DOMAIN_SUFFIX, "*.example.com", AppRoute.VPN))
        assertNull(DestinationRules.create(DestinationRuleType.DOMAIN, "bad_domain.example", AppRoute.VPN))
    }

    @Test fun `ipv4 cidr normalizes host bits and rejects ambiguous input`() {
        assertEquals(
            "192.168.1.0/24",
            DestinationRules.create(DestinationRuleType.IP_CIDR, "192.168.1.42/24", AppRoute.DPI)?.value,
        )
        assertEquals(
            "0.0.0.0/0",
            DestinationRules.create(DestinationRuleType.IP_CIDR, "203.0.113.7/0", AppRoute.DIRECT)?.value,
        )
        assertNull(DestinationRules.create(DestinationRuleType.IP_CIDR, "192.168.001.1/24", AppRoute.VPN))
    }

    @Test fun `ipv6 cidr normalizes host bits and rejects zones or invalid prefix`() {
        assertEquals(
            "2001:db8:abcd:1234::/64",
            DestinationRules.create(
                DestinationRuleType.IP_CIDR,
                "2001:DB8:ABCD:1234::42/64",
                AppRoute.VPN,
            )?.value,
        )
        assertEquals(
            "::/0",
            DestinationRules.create(DestinationRuleType.IP_CIDR, "2001:db8::1/0", AppRoute.DIRECT)?.value,
        )
        assertEquals(
            "2001:db8::/32",
            DestinationRules.create(
                DestinationRuleType.IP_CIDR,
                "2001:0db8:0000:0000::/32",
                AppRoute.DPI,
            )?.value,
        )
        assertNull(DestinationRules.create(DestinationRuleType.IP_CIDR, "fe80::1%wlan0/64", AppRoute.VPN))
        assertNull(DestinationRules.create(DestinationRuleType.IP_CIDR, "2001:db8::/129", AppRoute.VPN))
        assertNull(DestinationRules.create(DestinationRuleType.IP_CIDR, "::ffff:192.0.2.1/128", AppRoute.VPN))
    }

    @Test fun `json round trip keeps normalized rules`() {
        val rules = listOf(
            requireNotNull(DestinationRules.create(DestinationRuleType.DOMAIN, "one.example", AppRoute.VPN)),
            requireNotNull(DestinationRules.create(DestinationRuleType.DOMAIN_SUFFIX, "example.org", AppRoute.DPI)),
            requireNotNull(DestinationRules.create(DestinationRuleType.IP_CIDR, "8.8.8.8/32", AppRoute.DIRECT)),
            requireNotNull(DestinationRules.create(DestinationRuleType.IP_CIDR, "2001:db8::1/64", AppRoute.VPN)),
        )

        assertEquals(rules, DestinationRules.fromJsonStrict(DestinationRules.toJson(rules)))
    }

    @Test fun `corrupt stored rules fail closed`() {
        assertTrue(DestinationRules.fromStored("not-json").isEmpty())
        assertTrue(
            DestinationRules.fromStored(
                """[{"type":"DOMAIN","value":"bad domain","route":"VPN"}]""",
            ).isEmpty(),
        )
    }

    @Test fun `compiler order prefers specific domain and cidr matches`() {
        val rules = listOf(
            requireNotNull(DestinationRules.create(DestinationRuleType.IP_CIDR, "10.0.0.0/8", AppRoute.VPN)),
            requireNotNull(DestinationRules.create(DestinationRuleType.DOMAIN_SUFFIX, "example.com", AppRoute.VPN)),
            requireNotNull(DestinationRules.create(DestinationRuleType.IP_CIDR, "10.1.0.0/16", AppRoute.DPI)),
            requireNotNull(DestinationRules.create(DestinationRuleType.DOMAIN, "api.example.com", AppRoute.DIRECT)),
            requireNotNull(DestinationRules.create(DestinationRuleType.DOMAIN_SUFFIX, "deep.example.com", AppRoute.DPI)),
            requireNotNull(DestinationRules.create(DestinationRuleType.IP_CIDR, "2001:db8::/32", AppRoute.VPN)),
            requireNotNull(DestinationRules.create(DestinationRuleType.IP_CIDR, "2001:db8:1::/48", AppRoute.DIRECT)),
        )

        assertEquals(
            listOf(
                "api.example.com",
                "deep.example.com",
                "example.com",
                "2001:db8:1::/48",
                "2001:db8::/32",
                "10.1.0.0/16",
                "10.0.0.0/8",
            ),
            DestinationRules.orderedForCompilation(rules).map { it.value },
        )
    }
}

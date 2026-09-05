package dev.triplet.app.ui

import dev.triplet.app.R
import dev.triplet.app.core.AppRoute
import dev.triplet.app.core.DestinationRuleType
import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationRulesPresentationTest {
    @Test fun `rule types map to distinct labels`() {
        assertEquals(R.string.destination_rules_type_domain, destinationRuleTypeLabelRes(DestinationRuleType.DOMAIN))
        assertEquals(
            R.string.destination_rules_type_suffix,
            destinationRuleTypeLabelRes(DestinationRuleType.DOMAIN_SUFFIX),
        )
        assertEquals(R.string.destination_rules_type_cidr, destinationRuleTypeLabelRes(DestinationRuleType.IP_CIDR))
    }

    @Test fun `routes reuse app route labels`() {
        assertEquals(R.string.route_direct, destinationRuleRouteLabelRes(AppRoute.DIRECT))
        assertEquals(R.string.route_vpn, destinationRuleRouteLabelRes(AppRoute.VPN))
        assertEquals(R.string.route_dpi, destinationRuleRouteLabelRes(AppRoute.DPI))
    }
}

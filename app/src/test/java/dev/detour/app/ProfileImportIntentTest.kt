package dev.detour.app

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class ProfileImportIntentTest {
    private val vless =
        "vless://b831381d-6324-4d53-ad4f-8cda48b30811@example.com:443" +
            "?type=tcp&security=reality&fp=chrome&sni=example.com" +
            "&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&sid=6ba85179" +
            "&flow=xtls-rprx-vision"

    @Test fun `vless action view opens profile import`() {
        val request = profileImportRequest(
            action = Intent.ACTION_VIEW,
            mimeType = null,
            data = vless,
            sharedText = null,
        )
        assertEquals(vless, request?.value)
        assertEquals(false, request?.subscription)
    }

    @Test fun `detour import wrapper accepts subscription`() {
        val subscription = "https://subscription.example/token"
        val encoded = URLEncoder.encode(subscription, StandardCharsets.UTF_8.name())
        val request = profileImportRequest(
            action = Intent.ACTION_VIEW,
            mimeType = null,
            data = "detour://import?url=$encoded",
            sharedText = null,
        )

        assertEquals(subscription, request?.value)
        assertTrue(request?.subscription == true)
    }

    @Test fun `plain text share accepts valid profile only`() {
        assertEquals(
            vless,
            profileImportRequest(
                action = Intent.ACTION_SEND,
                mimeType = "text/plain",
                data = null,
                sharedText = vless,
            )?.value,
        )
        assertNull(
            profileImportRequest(
                action = Intent.ACTION_SEND,
                mimeType = "text/plain",
                data = null,
                sharedText = "look at this $vless",
            ),
        )
    }

    @Test fun `non text shares and unrelated links are ignored`() {
        assertNull(
            profileImportRequest(
                action = Intent.ACTION_SEND,
                mimeType = "application/octet-stream",
                data = null,
                sharedText = vless,
            ),
        )
        assertNull(
            profileImportRequest(
                action = Intent.ACTION_VIEW,
                mimeType = null,
                data = "https://example.com",
                sharedText = null,
            ),
        )
    }
}

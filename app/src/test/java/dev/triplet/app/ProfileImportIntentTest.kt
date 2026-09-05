package dev.triplet.app

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileImportIntentTest {
    private val vless =
        "vless://b831381d-6324-4d53-ad4f-8cda48b30811@example.com:443" +
            "?type=tcp&security=reality&fp=chrome&sni=example.com" +
            "&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&sid=6ba85179" +
            "&flow=xtls-rprx-vision"

    @Test fun `vless action view opens profile import`() {
        val request = profileImportRequest(
            Intent(Intent.ACTION_VIEW, Uri.parse(vless)),
        )
        assertEquals(vless, request?.value)
        assertEquals(false, request?.subscription)
    }

    @Test fun `detour import wrapper accepts subscription`() {
        val subscription = "https://subscription.example/token"
        val uri = Uri.Builder()
            .scheme("detour")
            .authority("import")
            .appendQueryParameter("url", subscription)
            .build()
        val request = profileImportRequest(Intent(Intent.ACTION_VIEW, uri))

        assertEquals(subscription, request?.value)
        assertTrue(request?.subscription == true)
    }

    @Test fun `plain text share accepts valid profile only`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, vless)
        }
        assertEquals(vless, profileImportRequest(intent)?.value)

        intent.putExtra(Intent.EXTRA_TEXT, "look at this $vless")
        assertNull(profileImportRequest(intent))
    }

    @Test fun `non text shares and unrelated links are ignored`() {
        val binaryShare = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TEXT, vless)
        }
        assertNull(profileImportRequest(binaryShare))
        assertNull(profileImportRequest(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))))
    }
}

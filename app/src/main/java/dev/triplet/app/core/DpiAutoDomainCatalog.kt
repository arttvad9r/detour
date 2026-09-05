package dev.triplet.app.core

/**
 * Built-in AUTO probe targets.
 *
 * Host coverage includes ByeByeDPI's proxy-test site assets at commit
 * 01b080e2fe41898d8371495a9db887da54e28798 (2026-08-31), plus the stable
 * Detour anchors that were already shipped before this catalog expansion.
 * The hosts are probe evidence, not a copy of ByeByeDPI's runtime routing
 * model: Detour maps related hosts to stable ByeDPI rule scopes so ephemeral
 * CDN names (notably googlevideo.com) produce useful future-facing rules.
 */
object DpiAutoDomainCatalog {
    const val SOURCE_COMMIT = "01b080e2fe41898d8371495a9db887da54e28798"

    val default: List<DpiDomainGroup> = listOf(
        group(
            id = "youtube",
            hosts = listOf(
                // Existing Detour anchor retained in addition to ByeByeDPI's list.
                "www.youtube.com" to "youtube.com",
                "youtu.be" to "youtu.be",
                "youtube.com" to "youtube.com",
                "i.ytimg.com" to "ytimg.com",
                "i9.ytimg.com" to "ytimg.com",
                "yt3.ggpht.com" to "ggpht.com",
                "yt4.ggpht.com" to "ggpht.com",
                "googleapis.com" to "googleapis.com",
                "jnn-pa.googleapis.com" to "googleapis.com",
                "googleusercontent.com" to "googleusercontent.com",
                "signaler-pa.youtube.com" to "youtube.com",
                "youtubei.googleapis.com" to "googleapis.com",
                "manifest.googlevideo.com" to "googlevideo.com",
                "yt3.googleusercontent.com" to "googleusercontent.com",
            ),
        ),
        group(
            id = "googlevideo",
            hosts = listOf(
                // Stable Detour anchor retained in addition to regional CDN probes.
                "redirector.googlevideo.com",
                "rr1---sn-4axm-n8vs.googlevideo.com",
                "rr1---sn-gvnuxaxjvh-o8ge.googlevideo.com",
                "rr1---sn-ug5onuxaxjvh-p3ul.googlevideo.com",
                "rr1---sn-ug5onuxaxjvh-n8v6.googlevideo.com",
                "rr4---sn-q4flrnsl.googlevideo.com",
                "rr10---sn-gvnuxaxjvh-304z.googlevideo.com",
                "rr14---sn-n8v7kn7r.googlevideo.com",
                "rr16---sn-axq7sn76.googlevideo.com",
                "rr1---sn-8ph2xajvh-5xge.googlevideo.com",
                "rr1---sn-gvnuxaxjvh-5gie.googlevideo.com",
                "rr12---sn-gvnuxaxjvh-bvwz.googlevideo.com",
                "rr5---sn-n8v7knez.googlevideo.com",
                "rr1---sn-u5uuxaxjvhg0-ocje.googlevideo.com",
                "rr2---sn-q4fl6ndl.googlevideo.com",
                "rr5---sn-gvnuxaxjvh-n8vk.googlevideo.com",
                "rr4---sn-jvhnu5g-c35d.googlevideo.com",
                "rr1---sn-q4fl6n6y.googlevideo.com",
                "rr2---sn-hgn7ynek.googlevideo.com",
                "rr1---sn-xguxaxjvh-gufl.googlevideo.com",
            ).map { it to "googlevideo.com" },
        ),
        group(
            id = "discord",
            hosts = listOf(
                "dis.gd" to "dis.gd",
                "discord.co" to "discord.co",
                "discord.gg" to "discord.gg",
                // Existing Detour anchor retained under the discord.gg scope.
                "gateway.discord.gg" to "discord.gg",
                "discord.app" to "discord.app",
                "discord.com" to "discord.com",
                "discord.dev" to "discord.dev",
                "discord.new" to "discord.new",
                "discord.gift" to "discord.gift",
                "discord.gifts" to "discord.gifts",
                "discord.media" to "discord.media",
                "discord.store" to "discord.store",
                "discord.design" to "discord.design",
                "discordapp.com" to "discordapp.com",
                "discordcdn.com" to "discordcdn.com",
                "discordsez.com" to "discordsez.com",
                "discordsays.com" to "discordsays.com",
                "discordmerch.com" to "discordmerch.com",
                "discordpartygames.com" to "discordpartygames.com",
                "discordactivities.com" to "discordactivities.com",
                "stable.dl2.discordapp.net" to "discordapp.net",
                "discord-attachments-uploads-prd.storage.googleapis.com" to
                    "discord-attachments-uploads-prd.storage.googleapis.com",
            ),
        ),
        group(
            id = "telegram",
            hosts = listOf(
                "telegram.org",
                "core.telegram.org",
                "web.telegram.org",
                "webk.telegram.org",
                "my.telegram.org",
                "translations.telegram.org",
                "instantview.telegram.org",
                "blog.telegram.org",
                "comments.telegram.org",
                "verify.telegram.org",
                "login.telegram.org",
                "auth.telegram.org",
                "api.telegram.org",
                "promo.telegram.org",
                "desktop.telegram.org",
                "macos.telegram.org",
                "ios.telegram.org",
                "android.telegram.org",
                "reactions.telegram.org",
                "claims.telegram.org",
                "x.telegram.org",
                "help.telegram.org",
                "docs.telegram.org",
                "schema.telegram.org",
                "dev.telegram.org",
                "contest.telegram.org",
                "premium.telegram.org",
                "settings.telegram.org",
                "qr.telegram.org",
                "stickers.telegram.org",
                "emoji.telegram.org",
                "themes.telegram.org",
                "donate.telegram.org",
                "fragment.telegram.org",
                "ton.telegram.org",
                "wallet.telegram.org",
                "pay.telegram.org",
                "voice.telegram.org",
                "cdn.telegram.org",
            ).map { it to "telegram.org" } + listOf(
                "telegram.me" to "telegram.me",
                "telegram.dog" to "telegram.dog",
                "telegra.ph" to "telegra.ph",
                "telesco.pe" to "telesco.pe",
                "web.telegram.me" to "telegram.me",
                "zws1.web.telegram.org" to "telegram.org",
                "zws2.web.telegram.org" to "telegram.org",
                "zws1.web.telegram.me" to "telegram.me",
                "zws2.web.telegram.me" to "telegram.me",
                "venus.web.telegram.org" to "telegram.org",
                "pluto.web.telegram.org" to "telegram.org",
                "aurora.web.telegram.org" to "telegram.org",
                "vesta.web.telegram.org" to "telegram.org",
            ),
        ),
    ).also { groups ->
        check(groups.map { it.id }.distinct().size == groups.size)
        check(groups.all { group -> group.targets.map { it.host }.distinct().size == group.targets.size })
    }

    private fun group(id: String, hosts: List<Pair<String, String>>): DpiDomainGroup =
        DpiDomainGroup(
            id = id,
            targets = hosts.mapIndexed { index, (host, scopeHost) ->
                DpiProbeTarget(
                    id = "$id-${index + 1}",
                    host = host,
                    scopeHost = scopeHost,
                )
            },
        )
}

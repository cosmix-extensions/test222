package com.videosfree

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI

// Regex used to detect obfuscated (packed) player javascript on LuluStream embeds.
private val PACKED_REGEX = Regex("""eval\(function\(p,a,c,k,e,d\)[\s\S]+?'\|'\)\)""")

private fun randomString(length: Int): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    return (1..length).map { chars.random() }.joinToString("")
}

/**
 * DoodStream family extractor (myvidplay.com and mirrors).
 * Flow: embed page -> /pass_md5/<hash>/<token> -> direct mp4 url.
 */
suspend fun extractDood(embedUrl: String, callback: (ExtractorLink) -> Unit): Boolean {
    return try {
        val host = URI(embedUrl).host ?: return false
        val embedHeaders = mapOf(
            "User-Agent" to VideosFreeProvider.USER_AGENT,
            "Referer" to embedUrl,
        )
        val html = app.get(embedUrl, headers = embedHeaders).text
        val passPath = Regex("""/pass_md5/[^'"\s]+""").find(html)?.value ?: return false
        val token = passPath.substringAfterLast('/')

        val base = app.get("https://$host$passPath", headers = embedHeaders)
            .text.trim()
            .let { if (it.startsWith("http")) it else "h$it" }
        if (!base.startsWith("http")) return false

        val streamUrl = base + randomString(10) + "?token=$token"

        callback.invoke(
            newExtractorLink("DoodStream", "DoodStream", streamUrl, ExtractorLinkType.VIDEO) {
                quality = Qualities.Unknown.value
                headers = mapOf(
                    "User-Agent" to VideosFreeProvider.USER_AGENT,
                    "Referer" to "https://$host/",
                )
            }
        )
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

/**
 * LuluStream family extractor (xxvideoss.net embed).
 * Flow: embed page -> unpack packed player javascript -> direct mp4/m3u8 url.
 * The CDN rejects requests without a browser User-Agent, so the link must
 * carry an explicit User-Agent header.
 */
suspend fun extractLulu(embedUrl: String, callback: (ExtractorLink) -> Unit): Boolean {
    return try {
        val host = URI(embedUrl).host ?: return false
        val embedHeaders = mapOf(
            "User-Agent" to VideosFreeProvider.USER_AGENT,
            "Referer" to embedUrl,
        )
        val html = app.get(embedUrl, headers = embedHeaders).text
        val packed = PACKED_REGEX.find(html)?.value ?: return false
        val unpacked = JsUnpacker(packed).unpack() ?: return false

        val streamUrl = Regex("""(?:https?:)?//[^"']+\.(?:mp4|m3u8)[^"']*""")
            .find(unpacked)?.value
            ?.replace("\\/", "/")
            ?.let { if (it.startsWith("http")) it else "https:$it" }
            ?: return false

        val isHls = streamUrl.contains(".m3u8")

        // Validate the manifest and detect the real quality from it.
        var quality = Qualities.Unknown.value
        if (isHls) {
            try {
                val manifest = app.get(streamUrl, headers = embedHeaders).text
                quality = when {
                    manifest.contains("1920x1080") -> Qualities.P1080.value
                    manifest.contains("1280x720") -> Qualities.P720.value
                    manifest.contains("856x480") || manifest.contains("854x480") ||
                        manifest.contains("640x360") -> Qualities.P480.value
                    else -> Qualities.Unknown.value
                }
            } catch (_: Exception) {
                // Keep the link even if the manifest check fails.
            }
        }

        callback.invoke(
            newExtractorLink(
                "LuluStream",
                "LuluStream",
                streamUrl,
                if (isHls) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            ) {
                referer = "https://$host/"
                this.quality = quality
                headers = mapOf(
                    "User-Agent" to VideosFreeProvider.USER_AGENT,
                    "Referer" to "https://$host/",
                )
            }
        )
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

/**
 * Pornflicks extractor (stream.pornflicks.tv).
 * Flow: embed /e/<filecode> -> POST JSON to /api/stream -> m3u8 url.
 */
suspend fun extractPornflicks(embedUrl: String, callback: (ExtractorLink) -> Unit): Boolean {
    return try {
        val filecode = embedUrl.substringAfter("/e/").substringBefore('/')
            .takeIf { it.isNotBlank() } ?: return false

        val res = app.post(
            "https://stream.pornflicks.tv/api/stream",
            json = mapOf(
                "filecode" to filecode,
                "device" to "desktop",
                "codecs" to listOf("h264", "vp9"),
            ),
            headers = mapOf(
                "User-Agent" to VideosFreeProvider.USER_AGENT,
                "Referer" to embedUrl,
                "X-Requested-With" to "XMLHttpRequest",
            )
        ).text

        val streamUrl = Regex("\"streaming_url\":\"([^\"]+)\"").find(res)
            ?.groupValues?.get(1) ?: return false

        callback.invoke(
            newExtractorLink("Pornflicks", "Pornflicks", streamUrl, ExtractorLinkType.M3U8) {
                quality = Qualities.Unknown.value
                headers = mapOf(
                    "User-Agent" to VideosFreeProvider.USER_AGENT,
                    "Referer" to "https://stream.pornflicks.tv/",
                )
            }
        )
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

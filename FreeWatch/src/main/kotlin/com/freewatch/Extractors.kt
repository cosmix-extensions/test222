package com.freewatch

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject

class Vixeo : ExtractorApi() {
    override val name = "Vixeo"
    override val mainUrl = "https://vixeo.io"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val html = app.get(url, referer = referer ?: mainUrl).text
        val blob = Regex("""data-config=["']([A-Za-z0-9+/=_-]+)["']""")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?: return

        val pad = (4 - blob.length % 4) % 4
        val json = try {
            base64Decode(blob + "=".repeat(pad))
        } catch (_: Exception) {
            return
        }

        val sourceHex = Regex(""""source"\s*:\s*"([^"]+)"""")
            .find(json)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("|", "")
            ?: return

        if (sourceHex.length < 4 || sourceHex.length % 2 != 0) return

        val decoded = try {
            sourceHex.chunked(2)
                .map { it.toInt(16).toChar() }
                .joinToString("")
                .reversed()
        } catch (_: Exception) {
            return
        }

        if (!decoded.startsWith("http")) return

        val type = if (decoded.contains(".m3u8")) {
            ExtractorLinkType.M3U8
        } else {
            ExtractorLinkType.VIDEO
        }

        callback.invoke(
            newExtractorLink(name, name, decoded, type) {
                this.referer = "$mainUrl/"
                this.headers = mapOf("Origin" to mainUrl)
                this.quality = Qualities.Unknown.value
            }
        )
    }
}

class Luvstream : ExtractorApi() {
    override val name = "Luvstream"
    override val mainUrl = "https://luvstream.cc"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val doc = app.get(url, referer = referer ?: mainUrl).document
        val mp4 = doc.selectFirst("video source[src], source[src*=.mp4]")?.attr("src")
            ?: Regex("""src=["'](https?://[^"']+\.mp4[^"']*)["']""")
                .find(doc.html())
                ?.groupValues
                ?.getOrNull(1)
            ?: return

        callback.invoke(
            newExtractorLink(name, name, mp4, ExtractorLinkType.VIDEO) {
                this.referer = "$mainUrl/"
                this.quality = Qualities.Unknown.value
            }
        )
    }
}

open class Firestream : ExtractorApi() {
    override val name = "Firestream"
    override val mainUrl = "https://firestream.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val page = app.get(url, referer = referer ?: mainUrl)
        val html = page.text
        val finalUrl = page.url

        val blob = Regex(
            """<script[^>]*id=["']token-blob["'][^>]*>([^<]+)</script>""",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.getOrNull(1)?.trim()
            ?: Regex("""id=["']token-blob["'][^>]*>\s*([A-Za-z0-9+/=_-]+)\s*<""")
                .find(html)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
            ?: return

        val slug = finalUrl.substringAfter("/e/").substringBefore("?").substringBefore("/").trim('/')
            .ifBlank {
                url.substringAfter("/e/").substringBefore("?").substringBefore("/").trim('/')
            }
        if (slug.isBlank()) return

        val origin = Regex("""^(https?://[^/]+)""").find(finalUrl)?.groupValues?.getOrNull(1)
            ?: mainUrl

        val resolveUrl = "$origin/api/videos/$slug/resolve"
        val body = app.post(
            resolveUrl,
            headers = mapOf(
                "Content-Type" to "application/json",
                "Accept" to "application/json",
                "Referer" to finalUrl,
                "Origin" to origin,
            ),
            json = mapOf("blob" to blob),
        ).text

        val obj = try {
            JSONObject(body)
        } catch (_: Exception) {
            return
        }

        val headers = mapOf(
            "Referer" to "$origin/",
            "Origin" to origin,
        )

        val m3u8 = obj.optString("signedVideoUrl").ifBlank { null }
        val mp4 = obj.optString("signedVideoMp4Url").ifBlank { null }

        if (!m3u8.isNullOrBlank()) {
            callback.invoke(
                newExtractorLink(name, "$name HLS", m3u8, ExtractorLinkType.M3U8) {
                    this.referer = "$origin/"
                    this.headers = headers
                    this.quality = Qualities.Unknown.value
                }
            )
        }

        if (!mp4.isNullOrBlank()) {
            callback.invoke(
                newExtractorLink(name, "$name MP4", mp4, ExtractorLinkType.VIDEO) {
                    this.referer = "$origin/"
                    this.headers = headers
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}

class Frstream : Firestream() {
    override val mainUrl = "https://frstream.life"
    override val name = "Firestream"
}

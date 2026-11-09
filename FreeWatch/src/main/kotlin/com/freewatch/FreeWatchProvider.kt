package com.freewatch

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URLEncoder

class FreeWatchProvider : MainAPI() {
    override var mainUrl = "https://xxxfree.watch"
    override var name = "FreeWatch"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Others)

    private val perPage = 20
    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private var antibotToken: String? = null
    private var antibotAt: Long = 0L
    private val antibotTtlMs = 20 * 60 * 1000L

    private val defaultHeaders: Map<String, String>
        get() = mapOf(
            "User-Agent" to userAgent,
            "Accept-Language" to "en-US,en;q=0.9",
        )

    override val mainPage = mainPageOf(
        "latest" to "Latest Video",
        "11" to "Brazzers Free",
        "726" to "Brazzers New Clips",
        "8" to "MILF",
        "1246" to "Teamskeet Free",
    )

    private fun encode(text: String): String {
        return try {
            URLEncoder.encode(text, "UTF-8")
        } catch (_: Exception) {
            text
        }
    }

    private fun cookieHeaders(): Map<String, String> {
        val token = antibotToken ?: return emptyMap()
        return mapOf("Cookie" to "antibot=$token")
    }

    private fun extractAntibotToken(html: String): String? {
        val atobRegex = Regex("""atob\(["']([A-Za-z0-9+/=]+)["']\)""")
        for (match in atobRegex.findAll(html)) {
            val decoded = try {
                base64Decode(match.groupValues[1])
            } catch (_: Exception) {
                continue
            }
            Regex("""name=["']antibot["'][^>]*value=["']([a-f0-9]{32})["']""")
                .find(decoded)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { return it }

            if (decoded.matches(Regex("[a-f0-9]{32}"))) return decoded
        }
        return Regex("""value=["']([a-f0-9]{32})["']""").find(html)?.groupValues?.getOrNull(1)
    }

    private suspend fun ensureAntibot() {
        val now = System.currentTimeMillis()
        if (!antibotToken.isNullOrBlank() && now - antibotAt < antibotTtlMs) return

        val challenge = app.get(mainUrl, headers = defaultHeaders).text
        val token = extractAntibotToken(challenge)
            ?: throw ErrorLoadingException("Antibot token not found")

        app.post(
            mainUrl,
            headers = defaultHeaders + mapOf(
                "Referer" to mainUrl,
                "Origin" to mainUrl,
                "Content-Type" to "application/x-www-form-urlencoded",
            ),
            data = mapOf(
                "antibot" to token,
                "submit" to "Click to continue",
            ),
        )

        antibotToken = token
        antibotAt = now
    }

    private fun apiHeaders(): Map<String, String> {
        return defaultHeaders + cookieHeaders() + mapOf(
            "Accept" to "application/json",
            "Referer" to "$mainUrl/",
        )
    }

    private fun htmlHeaders(): Map<String, String> {
        return defaultHeaders + cookieHeaders() + mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Referer" to "$mainUrl/",
        )
    }

    private fun decodeHtml(text: String): String {
        return text
            .replace("&#8211;", "–")
            .replace("&#8217;", "'")
            .replace("&#038;", "&")
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&quot;", "\"")
            .replace("<p>", "")
            .replace("</p>", "")
            .trim()
    }

    private fun WpPost.toSearchResponse(): SearchResponse? {
        val title = decodeHtml(this.title?.rendered.orEmpty()).ifBlank { return null }
        val url = this.link?.ifBlank { null } ?: return null
        val poster = this.embedded?.featuredMedia?.firstOrNull()?.sourceUrl
        return newMovieSearchResponse(title, url, TvType.Others) {
            this.posterUrl = poster
        }
    }

    private fun buildPostsUrl(data: String, page: Int): String {
        return if (data == "latest") {
            "$mainUrl/wp-json/wp/v2/posts?page=$page&per_page=$perPage&orderby=date&order=desc&_embed=1"
        } else {
            "$mainUrl/wp-json/wp/v2/posts?categories=$data&page=$page&per_page=$perPage&orderby=date&order=desc&_embed=1"
        }
    }

    private suspend fun fetchPosts(url: String): Pair<List<SearchResponse>, Boolean> {
        ensureAntibot()
        val res = app.get(url, headers = apiHeaders())
        val totalPages = res.headers["X-WP-TotalPages"]?.toIntOrNull()
        val list = try {
            parseJson<ArrayList<WpPost>>(res.text)
        } catch (_: Exception) {
            emptyList()
        }
        val items = list.mapNotNull { it.toSearchResponse() }
        val page = Regex("""[?&]page=(\d+)""").find(url)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        val hasNext = when {
            totalPages != null -> page < totalPages
            else -> items.size >= perPage
        }
        return items to hasNext
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val (items, hasNext) = fetchPosts(buildPostsUrl(request.data, page))
        return newHomePageResponse(
            HomePageList(request.name, items, isHorizontalImages = true),
            hasNext
        )
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url =
            "\( mainUrl/wp-json/wp/v2/posts?search= \){encode(query)}&page=$page&per_page=$perPage&_embed=1"
        val (items, hasNext) = fetchPosts(url)
        return newSearchResponseList(items, hasNext)
    }

    private fun parseRelated(html: String): List<SearchResponse> {
        val idx = html.indexOf("Related videos", ignoreCase = true)
        if (idx < 0) return emptyList()
        val block = html.substring(idx, minOf(html.length, idx + 25000))
        val out = ArrayList<SearchResponse>()
        val articleRegex = Regex("""<article[\s\S]*?</article>""", RegexOption.IGNORE_CASE)
        for (article in articleRegex.findAll(block)) {
            val body = article.value
            val href = Regex(
                """href=["'](https?://xxxfree\.watch/[^"']+)["']""",
                RegexOption.IGNORE_CASE
            ).findAll(body)
                .map { it.groupValues[1] }
                .firstOrNull { link ->
                    !link.contains("/category/") &&
                        !link.contains("/tag/") &&
                        !link.contains("/page/")
                } ?: continue

            val title = Regex("""title=["']([^"']+)["']""").find(body)?.groupValues?.getOrNull(1)
                ?: Regex("""alt=["']([^"']+)["']""").find(body)?.groupValues?.getOrNull(1)
                ?: continue

            val poster = Regex(
                """(?:data-src|data-lazy-src|src)=["']([^"']*wp-content/uploads[^"']+)["']"""
            ).find(body)?.groupValues?.getOrNull(1)

            out.add(
                newMovieSearchResponse(decodeHtml(title), href, TvType.Others) {
                    this.posterUrl = poster
                }
            )
        }
        return out.distinctBy { it.url }
    }

    private fun parseEmbeds(html: String): List<String> {
        val embeds = LinkedHashSet<String>()
        val playerIdx = html.indexOf("video-player")
        val scope = if (playerIdx >= 0) {
            html.substring(playerIdx, minOf(html.length, playerIdx + 8000))
        } else {
            html
        }
        val regex = Regex(
            """(?:data-lazy-src|data-src|src)=["'](https?://[^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        for (match in regex.findAll(scope)) {
            val src = match.groupValues[1]
            if (src == "about:blank") continue
            if (
                src.contains("/e/") ||
                src.contains("embed") ||
                src.contains("player") ||
                src.contains("stream")
            ) {
                embeds.add(src)
            }
        }
        return embeds.toList()
    }

    override suspend fun load(url: String): LoadResponse {
        ensureAntibot()
        val slug = url.trimEnd('/').substringAfterLast('/').substringBefore('?')
        val apiUrl = "\( mainUrl/wp-json/wp/v2/posts?slug= \){encode(slug)}&_embed=1"
        val apiRes = app.get(apiUrl, headers = apiHeaders())
        val post = try {
            parseJson<ArrayList<WpPost>>(apiRes.text).firstOrNull()
        } catch (_: Exception) {
            null
        }

        val pageHtml = app.get(url, headers = htmlHeaders()).text
        val title = decodeHtml(post?.title?.rendered.orEmpty())
            .ifBlank {
                Regex("""property=["']og:title["']\s+content=["']([^"']+)["']""")
                    .find(pageHtml)?.groupValues?.getOrNull(1)
                    ?.substringBefore(" - WatchXXXFree")
                    ?.trim()
                    .orEmpty()
            }
            .ifBlank { slug.replace('-', ' ') }

        val poster = post?.embedded?.featuredMedia?.firstOrNull()?.sourceUrl
            ?: Regex("""property=["']og:image["']\s+content=["']([^"']+)["']""")
                .find(pageHtml)?.groupValues?.getOrNull(1)

        val plot = decodeHtml(post?.content?.rendered.orEmpty())
            .ifBlank {
                Regex("""property=["']og:description["']\s+content=["']([^"']+)["']""")
                    .find(pageHtml)?.groupValues?.getOrNull(1)
                    .orEmpty()
            }
            .replace(Regex("""<[^>]+>"""), "")
            .trim()
            .ifBlank { null }

        val year = post?.date?.take(4)?.toIntOrNull()
        val recommendations = parseRelated(pageHtml)

        return newMovieLoadResponse(title, url, TvType.Others, url) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        ensureAntibot()
        val html = app.get(data, headers = htmlHeaders()).text
        val embeds = parseEmbeds(html)
        if (embeds.isEmpty()) return false

        var found = false
        for (embed in embeds) {
            try {
                if (loadExtractor(embed, data, subtitleCallback, callback)) {
                    found = true
                }
            } catch (_: Exception) {
            }
        }
        return found
    }

    private data class WpPost(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("date") val date: String? = null,
        @JsonProperty("slug") val slug: String? = null,
        @JsonProperty("link") val link: String? = null,
        @JsonProperty("title") val title: WpRendered? = null,
        @JsonProperty("content") val content: WpRendered? = null,
        @JsonProperty("featured_media") val featuredMedia: Int? = null,
        @JsonProperty("categories") val categories: List<Int>? = null,
        @JsonProperty("_embedded") val embedded: WpEmbedded? = null,
    )

    private data class WpRendered(
        @JsonProperty("rendered") val rendered: String? = null,
    )

    private data class WpEmbedded(
        @JsonProperty("wp:featuredmedia") val featuredMedia: List<WpMedia>? = null,
    )

    private data class WpMedia(
        @JsonProperty("source_url") val sourceUrl: String? = null,
    )
}

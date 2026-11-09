package com.videosfree

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class VideosFreeProvider : MainAPI() {

    override var mainUrl = "https://xxvideoss.org"
    override var name = "XXVideoss"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Others)

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        val browserHeaders = mapOf("User-Agent" to USER_AGENT)
    }

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Home",
        "$mainUrl/category/brazzers/" to "Brazzers",
        "$mainUrl/category/bang-bros/" to "Bang Bros",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else request.data.trimEnd('/') + "/page/$page/"
        val doc = app.get(url, headers = browserHeaders, timeout = 60).document
        val items = doc.select("article.postbox").mapNotNull { it.toSearchResponse() }
        val hasNext = doc.selectFirst("a.next.page-numbers") != null
        return newHomePageResponse(
            HomePageList(request.name, items, isHorizontalImages = true),
            hasNext
        )
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val href = this.selectFirst("a[href]")?.attr("href") ?: return null
        val title = this.selectFirst("h2.entry-title")?.text()?.trim() ?: return null
        val poster = this.selectFirst(".postboximage img")?.attr("src")
        return newMovieSearchResponse(title, href, TvType.Others) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val q = java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        val url = if (page <= 1) "$mainUrl/?s=$q" else "$mainUrl/page/$page/?s=$q"
        val doc = app.get(url, headers = browserHeaders, timeout = 60).document
        val items = doc.select("article.postbox").mapNotNull { it.toSearchResponse() }
        return newSearchResponseList(items, items.isNotEmpty())
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        return search(query, 1)?.items?.take(5)
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = browserHeaders, timeout = 60).document
        val title = doc.selectFirst("h1.entry-title")?.text()?.trim()
            ?: doc.title().substringBefore(" - XXVideoss").trim()
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val plot = doc.selectFirst("meta[name=description]")?.attr("content")

        // "You May Also Like..." related videos listed under the player.
        val recommendations = doc.select(".relatedpostlistcontainer article.postbox")
            .mapNotNull { it.toSearchResponse() }

        return newMovieLoadResponse(title, url, TvType.Others, url) {
            this.posterUrl = poster
            this.plot = plot
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val doc = app.get(data, headers = browserHeaders, timeout = 60).document
            val iframes = doc.select("iframe").mapNotNull { iframe ->
                iframe.attr("src").takeIf { it.startsWith("http") }
            }

            var found = false
            for (iframe in iframes) {
                val extracted = when {
                    iframe.contains("pornflicks", ignoreCase = true) ->
                        extractPornflicks(iframe, callback)
                    iframe.contains("lulustream", ignoreCase = true) ||
                        iframe.contains("xxvideoss.net", ignoreCase = true) ->
                        extractLulu(iframe, callback)
                    else ->
                        // Unknown iframe host: try the custom Dood-family and Lulu
                        // extractors first, then fall back to CloudStream's built-in
                        // extractors for anything else.
                        extractDood(iframe, callback) ||
                            extractLulu(iframe, callback) ||
                            loadExtractor(iframe, data, subtitleCallback, callback)
                }
                if (extracted) found = true
            }
            found
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

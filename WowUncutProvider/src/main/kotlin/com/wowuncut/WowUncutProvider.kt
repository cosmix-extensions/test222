package com.wowuncut

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class WowUncutProvider : MainAPI() {
    override var mainUrl = "https://wowuncut.com"
    override var name = "WowUncut"
    override var lang = "hi"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Others)

    private val ua = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

    override val mainPage = mainPageOf(
        "$mainUrl/tag/niksindian-porn-videos-download?filter=latest" to "🔥 NiksIndian",
        "$mainUrl/category/hindi-web-series/" to "Hindi Web Series",
        "$mainUrl/category/bengali-web-series/" to "Bengali Web Series",
        "$mainUrl/category/tamil-web-series/" to "Tamil Web Series",
        "$mainUrl/category/telugu-web-series/" to "Telugu Web Series",
        "$mainUrl/category/hindi-short-film/" to "Hindi Short Films",
        "$mainUrl/category/unrated-web-series/" to "Unrated Web Series",
        "$mainUrl/" to "Latest",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data.contains("tag/")) {
            if (page == 1) request.data
            else request.data.replace("?filter=latest", "/page/$page?filter=latest")
        } else {
            if (page == 1) request.data
            else request.data.trimEnd('/') + "/page/$page/"
        }
        val doc = app.get(url, headers = ua).document
        val items = doc.select("article.post, div.post-thumbnail, .item").mapNotNull { el ->
            val a = el.selectFirst("a[href]") ?: return@mapNotNull null
            val href = a.attr("abs:href").ifBlank { return@mapNotNull null }
            val title = (el.selectFirst("h2, h3, .entry-title")?.text() ?: a.attr("title")).trim().ifBlank { return@mapNotNull null }
            val poster = el.selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            val isSeries = title.contains("series", true) || title.contains("episode", true)
            if (isSeries) newTvSeriesSearchResponse(title, href, TvType.TvSeries) { posterUrl = poster }
            else newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = poster }
        }
        return newHomePageResponse(request.name, items, page < 12)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val url = if (page == 1) "$mainUrl/?s=${java.net.URLEncoder.encode(query, "UTF-8")}" else "$mainUrl/page/$page/?s=${java.net.URLEncoder.encode(query, "UTF-8")}"
        val doc = app.get(url, headers = ua).document
        val items = doc.select("article.post").mapNotNull { el ->
            val a = el.selectFirst("a[href]") ?: return@mapNotNull null
            val href = a.attr("abs:href").ifBlank { return@mapNotNull null }
            val title = (el.selectFirst("h2, h3")?.text() ?: a.attr("title")).trim().ifBlank { return@mapNotNull null }
            val poster = el.selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            val isSeries = title.contains("series", true) || title.contains("episode", true)
            if (isSeries) newTvSeriesSearchResponse(title, href, TvType.TvSeries) { posterUrl = poster }
            else newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = poster }
        }
        return newSearchResponseList(items, items.isNotEmpty())
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = ua).document
        val title = doc.selectFirst("h1.entry-title, h1")?.text()?.trim() ?: doc.title().trim()
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val description = doc.selectFirst("meta[property=og:description]")?.attr("content")
        val videoUrl = doc.selectFirst("meta[itemprop=contentURL]")?.attr("content") ?: ""
        val isSeries = title.contains("episode", true) || url.contains("episode", true)
        return if (isSeries && videoUrl.isNotBlank()) {
            val epNum = Regex("""episode[-\s]?(\d+)""", RegexOption.IGNORE_CASE).find(title + url)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, listOf(newEpisode(videoUrl) { name = "Episode $epNum"; episode = epNum; season = 1; this.posterUrl = poster })) { this.posterUrl = poster; this.plot = description }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, videoUrl) { this.posterUrl = poster; this.plot = description }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        if (data.isBlank() || !data.startsWith("http")) return false
        val quality = when { data.contains("1080") -> Qualities.P1080.value; data.contains("720") -> Qualities.P720.value; else -> Qualities.Unknown.value }
        callback(newExtractorLink(name, "$name [HD]", data, ExtractorLinkType.VIDEO) { this.quality = quality; this.headers = ua + mapOf("Referer" to mainUrl) })
        return true
    }
}


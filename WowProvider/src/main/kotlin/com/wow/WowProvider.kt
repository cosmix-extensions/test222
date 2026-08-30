package com.wow

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import java.util.regex.Pattern

class WowProvider : MainAPI() {
    override var mainUrl = "https://www.wowxxx.to"
    override var name = "Wow"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Others)

    private val ua = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")

    override val mainPage = mainPageOf(
        "$mainUrl/latest-updates/" to "Latest Updates",
        "$mainUrl/top-rated/" to "Top Rated",
        "$mainUrl/most-popular/" to "Most Popular",
        "$mainUrl/networks/mylf-com/latest-updates/" to "Mylf - Latest Updates",
        "$mainUrl/networks/mylf-com/" to "Mylf",
        "$mainUrl/networks/mylf-com/top-rated/" to "Mylf - Top Rated",
        "$mainUrl/networks/teamskeet-com/latest-updates/" to "Teamskeet - Latest Updates",
        "$mainUrl/networks/teamskeet-com/" to "Teamskeet",
        "$mainUrl/networks/teamskeet-com/top-rated/" to "Teamskeet - Top Rated",
        "$mainUrl/networks/mom-lover/latest-updates/" to "Mom Lover - Latest Updates",
        "$mainUrl/networks/mom-lover/most-popular/" to "Mom Lover - Most Popular",
        "$mainUrl/networks/mom-lover/top-rated/" to "Mom Lover - Top Rated",
        "$mainUrl/networks/nubiles-porn-com/latest-updates/" to "Nubiles Porn - Latest Updates",
        "$mainUrl/networks/nubiles-porn-com/" to "Nubiles Porn",
        "$mainUrl/networks/nubiles-porn-com/top-rated/" to "Nubiles Porn - Top Rated",
        "$mainUrl/sites/moms-teach-sex/latest-updates/" to "Moms Teach Sex - Latest Updates",
        "$mainUrl/sites/moms-teach-sex/most-popular/" to "Moms Teach Sex - Most Popular",
        "$mainUrl/sites/moms-teach-sex/top-rated/" to "Moms Teach Sex - Top Rated",
        "$mainUrl/sites/brattysis/latest-updates/" to "Brattysis - Latest Updates",
        "$mainUrl/sites/brattysis/most-popular/" to "Brattysis - Most Popular",
        "$mainUrl/sites/brattysis/top-rated/" to "Brattysis - Top Rated",
        "$mainUrl/sites/my-family-pies/latest-updates/" to "My Family Pies - Latest Updates",
        "$mainUrl/sites/my-family-pies/most-popular/" to "My Family Pies - Most Popular",
        "$mainUrl/sites/my-family-pies/top-rated/" to "My Family Pies - Top Rated",
        "$mainUrl/sites/youngermommy/latest-updates/" to "Youngermommy - Latest Updates",
        "$mainUrl/sites/youngermommy/most-popular/" to "Youngermommy - Most Popular",
        "$mainUrl/sites/youngermommy/top-rated/" to "Youngermommy - Top Rated",
        "$mainUrl/sites/sis-loves-me/latest-updates/" to "Sis Loves Me - Latest Updates",
        "$mainUrl/sites/sis-loves-me/most-popular/" to "Sis Loves Me - Most Popular",
        "$mainUrl/sites/sis-loves-me/top-rated/" to "Sis Loves Me - Top Rated",
        "$mainUrl/sites/perv-mom/latest-updates/" to "Perv Mom - Latest Updates",
        "$mainUrl/sites/perv-mom/most-popular/" to "Perv Mom - Most Popular",
        "$mainUrl/sites/perv-mom/top-rated/" to "Perv Mom - Top Rated",
        "$mainUrl/sites/freeusemilf/latest-updates/" to "Freeusemilf - Latest Updates",
        "$mainUrl/sites/freeusemilf/most-popular/" to "Freeusemilf - Most Popular",
        "$mainUrl/sites/freeusemilf/top-rated/" to "Freeusemilf - Top Rated",
        "$mainUrl/sites/analmom2/latest-updates/" to "Analmom2 - Latest Updates",
        "$mainUrl/sites/analmom2/most-popular/" to "Analmom2 - Most Popular",
        "$mainUrl/sites/analmom2/top-rated/" to "Analmom2 - Top Rated",
        "$mainUrl/sites/freeusefantasy/latest-updates/" to "Freeusefantasy - Latest Updates",
        "$mainUrl/sites/freeusefantasy/most-popular/" to "Freeusefantasy - Most Popular",
        "$mainUrl/sites/freeusefantasy/top-rated/" to "Freeusefantasy - Top Rated",
        "$mainUrl/sites/family-strokes/latest-updates/" to "Family Strokes - Latest Updates",
        "$mainUrl/sites/family-strokes/most-popular/" to "Family Strokes - Most Popular",
        "$mainUrl/sites/family-strokes/top-rated/" to "Family Strokes - Top Rated",
        "$mainUrl/sites/dad-crush/latest-updates/" to "Dad Crush - Latest Updates",
        "$mainUrl/sites/dad-crush/most-popular/" to "Dad Crush - Most Popular",
        "$mainUrl/sites/dad-crush/top-rated/" to "Dad Crush - Top Rated",
        "$mainUrl/sites/my-dirty-uncle/latest-updates/" to "My Dirty Uncle - Latest Updates",
        "$mainUrl/sites/my-dirty-uncle/most-popular/" to "My Dirty Uncle - Most Popular",
        "$mainUrl/models/eliza-ibarra/latest-updates/" to "Eliza Ibarra - Latest Updates",
        "$mainUrl/models/eliza-ibarra/" to "Eliza Ibarra",
        "$mainUrl/models/emily-willis/latest-updates/" to "Emily Willis - Latest Updates",
        "$mainUrl/models/emily-willis/" to "Emily Willis",
        "$mainUrl/models/valentina-nappi/latest-updates/" to "Valentina Nappi - Latest Updates",
        "$mainUrl/models/valentina-nappi/" to "Valentina Nappi",
        "$mainUrl/models/violet-myers/latest-updates/" to "Violet Myers - Latest Updates",
        "$mainUrl/models/violet-myers/" to "Violet Myers"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}$page/"
        val doc = app.get(url, headers = ua, timeout = 60).document

        val items = doc.select("div.item").mapNotNull { item ->
            val a = item.selectFirst("a[href*=/videos/]") ?: return@mapNotNull null
            val href = a.attr("href")
            val title = a.attr("title").trim().ifEmpty {
                item.selectFirst(".title")?.text()?.trim() ?: "Unknown"
            }

            var poster = item.selectFirst("img")?.let { img ->
                img.attr("data-src").ifEmpty { img.attr("src") }
            }

            if (poster?.startsWith("//") == true) poster = "https:$poster"
            if (poster?.startsWith("/") == true) poster = "$mainUrl$poster"

            newMovieSearchResponse(title, href, TvType.Others) {
                this.posterUrl = poster
            }
        }

        return newHomePageResponse(request.name, items, items.isNotEmpty())
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val q = java.net.URLEncoder.encode(query, "UTF-8").replace("+", "-")
        val url = if (page == 1) "$mainUrl/search/$q/relevance/" else "$mainUrl/search/$q/relevance/$page/"
        val document = app.get(url, headers = ua, timeout = 60).document

        val items = document.select("div.item").mapNotNull { item ->
            val a = item.selectFirst("a[href*=/videos/]") ?: return@mapNotNull null
            val href = a.attr("href")
            val title = a.attr("title").trim().ifEmpty {
                item.selectFirst(".title")?.text()?.trim() ?: "Unknown"
            }

            var poster = item.selectFirst("img")?.let { img ->
                img.attr("data-src").ifEmpty { img.attr("src") }
            }

            if (poster?.startsWith("//") == true) poster = "https:$poster"
            if (poster?.startsWith("/") == true) poster = "$mainUrl$poster"

            newMovieSearchResponse(title, href, TvType.Others) {
                this.posterUrl = poster
            }
        }

        val hasNext = items.isNotEmpty()
        return newSearchResponseList(items, hasNext)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        return search(query, 1)?.items?.take(5)
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = ua, timeout = 60).document
        val title = doc.title().trim().replace(" - wowxxx.to", "", true).trim()

        var poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
        if (poster == null) {
            poster = doc.selectFirst(".player-container img")?.attr("src")
        }

        val plotText = doc.selectFirst("meta[name=description]")?.attr("content")
        val tags = doc.select("div.item:has(span:contains(Categories)) a.link").map { it.text() }
        val actors = doc.select("div.item:has(span:contains(Pornstars)) a.btn_model").map { it.text() }

        val recommendations = doc.select("div.item:has(a[href*=/videos/])").mapNotNull { item ->
            val a = item.selectFirst("a[href*=/videos/]") ?: return@mapNotNull null
            val recHref = a.attr("href")
            val recTitle = a.attr("title").trim().ifEmpty {
                item.selectFirst(".title")?.text()?.trim() ?: "Unknown"
            }
            var recPoster = item.selectFirst("img")?.let { img ->
                img.attr("data-src").ifEmpty { img.attr("src") }
            }
            if (recPoster?.startsWith("//") == true) recPoster = "https:$recPoster"
            if (recPoster?.startsWith("/") == true) recPoster = "$mainUrl$recPoster"

            newMovieSearchResponse(recTitle, recHref, TvType.Others) {
                this.posterUrl = recPoster
            }
        }

        val videoId = doc.selectFirst("[data-object_id]")?.attr("data-object_id")
            ?: doc.selectFirst("[data-video-id]")?.attr("data-video-id")
            ?: Regex("""/(\d{6,})/""").find(url)?.groupValues?.get(1)
            ?: Regex("""/(\d{6,})/""").find(poster ?: "")?.groupValues?.get(1)

        val trailerUrl = videoId?.let { "https://cast.wowxxx.to/preview/$it.mp4" }

        return newMovieLoadResponse(title, url, TvType.Others, url) {
            this.posterUrl = poster
            this.plot = plotText
            this.tags = tags
            this.actors = actors.map { ActorData(Actor(it)) }
            this.recommendations = recommendations
            addTrailer(trailerUrl)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        if (data.isBlank()) return false
        try {
            val html = app.get(data, headers = ua, timeout = 60).text
            val matcher = Pattern.compile("src=['\"]([^'\"]*\\.mp4[^'\"]*)['\"]").matcher(html)
            var found = false
            while (matcher.find()) {
                var streamUrl = matcher.group(1) ?: continue
                if (streamUrl.startsWith("//")) streamUrl = "https:$streamUrl"

                val qualityMatch = Regex("(\\d{3,4})[mp]?\\.mp4").find(streamUrl)
                var qualityValue = Qualities.Unknown.value
                var qualityName = "Direct Stream"

                if (qualityMatch != null) {
                    val q = qualityMatch.groupValues[1].toIntOrNull() ?: 0
                    qualityName = "${q}p"
                    qualityValue = when (q) {
                        1080 -> Qualities.P1080.value
                        720 -> Qualities.P720.value
                        480 -> Qualities.P480.value
                        360 -> Qualities.P360.value
                        else -> Qualities.Unknown.value
                    }
                }

                callback.invoke(
                    newExtractorLink(
                        this.name,
                        qualityName,
                        streamUrl,
                        ExtractorLinkType.VIDEO
                    ) {
                        quality = qualityValue
                    }
                )
                found = true
            }
            return found
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
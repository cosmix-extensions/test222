package com.vipporns

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// ---------------------------------------------------------------------------
// Vipporns (www.vipporns.com)
// ---------------------------------------------------------------------------
// KVS (Kernel Video Sharing) tube site. Everything is read from the public
// pages and the standard KVS ajax block endpoints (HTML fragments, not JSON):
//
//   Listing page 1      /new-videos/ , /categories/{slug}/        (full HTML)
//   Listing page 2+     same url + ?mode=ajax&block_id=
//                       list_videos_common_videos_list&sort_by=post_date&from={page}
//   Search page 1       /search/{slugified query}/
//   Search page 2+      same url + ?mode=ajax&block_id=
//                       list_videos_videos_list_search_result&from={page}
//   Video page          /porn-videos/{id}/{slug}/
//                       -> inline "var flashvars = {...}" holds the direct
//                          mp4 url (video_url), duration, thumbnails, etc.
//   Recommendations     1) ".related-videos" block on the video page
//                       2) "More Videos with {model}" tab via ajax
//                          ?mode=ajax&block_id=list_videos_related_videos
//                          &mode_related={mode from data-parameters}
//                       3) player feed /related_videos_html/{id}/
//
// The site is a KVS CMS: thumbnails are landscape 16:9 screenshots
// (contents/videos_screenshots/...), so TvType.Others is used on purpose -
// CloudStream renders Others cards in a landscape layout on the main page.
class VippornsProvider : MainAPI() {
    override var mainUrl = "https://www.vipporns.com"
    override var name = "Vipporns"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Others)

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        // KVS ajax block ids
        private const val BLOCK_LIST = "list_videos_common_videos_list"
        private const val BLOCK_SEARCH = "list_videos_videos_list_search_result"
        private const val BLOCK_RELATED = "list_videos_related_videos"

        // A full page of the listing always contains 24 items.
        private const val PAGE_SIZE = 24

        private val VIDEO_ID_RE = Regex("""/porn-videos/(\d+)/""")
    }

    private fun pageHeaders(referer: String = mainUrl): Map<String, String> = mapOf(
        "User-Agent" to USER_AGENT,
        "Referer" to referer
    )

    // Search query must be a slug: KVS rejects %20 encoded paths with 403.
    private fun slugify(text: String): String =
        text.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    private fun videoIdOf(url: String): String? =
        VIDEO_ID_RE.find(url)?.groupValues?.get(1)

    // ------------------------------------------------------------------
    // Card parsing - shared by main page, search and the related blocks.
    // Markup: <div class="item"> <a href title> <img data-original .../>
    //         <strong class="title"> ... duration / rating / views
    // ------------------------------------------------------------------
    private fun parseCards(root: Element): List<SearchResponse> {
        return root.select(".list-videos .item").mapNotNull { item ->
            val a = item.selectFirst("a") ?: return@mapNotNull null
            val href = a.attr("href").ifBlank { return@mapNotNull null }
            val url = if (href.startsWith("http")) href else "$mainUrl$href"
            val title = a.attr("title").ifBlank {
                item.selectFirst("strong.title")?.text()
            }?.trim().orEmpty()
            if (title.isBlank()) return@mapNotNull null

            val img = item.selectFirst("img")
            val poster = img?.attr("data-original")?.takeIf { it.startsWith("http") }
                ?: img?.attr("src")?.takeIf { it.startsWith("http") && !it.startsWith("data:") }

            newMovieSearchResponse(title, url, TvType.Others) {
                this.posterUrl = poster
            }
        }
    }

    // ------------------------------------------------------------- mainPage
    // Rows follow the order of the site sections. Page 1 comes from the
    // plain page, every later page from the KVS ajax block endpoint.
    override val mainPage = mainPageOf(
        "new-videos/" to "New Videos",
        "categories/brazzers/" to "Brazzers",
        "categories/family-strokes/" to "Family Strokes",
        "categories/reality-kings/" to "Reality Kings",
        "categories/teamskett/" to "TeamSkett",
        "categories/freeusefantasy/" to "Freeuse Fantasy",
        "categories/mylf/" to "Mylf"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) {
            "$mainUrl/${request.data}"
        } else {
            val path = request.data.trimEnd('/')
            "$mainUrl/$path/?mode=ajax&block_id=$BLOCK_LIST&sort_by=post_date&from=$page"
        }

        val doc = app.get(url, headers = pageHeaders(), timeout = 30).document
        val items = parseCards(doc)
        val hasNext = items.size >= PAGE_SIZE
        // isHorizontalImages = true -> landscape 16:9 cards on the main page,
        // matching the site's screenshot thumbnails (same as HamsterProvider).
        return newHomePageResponse(
            HomePageList(request.name, items, isHorizontalImages = true),
            hasNext
        )
    }

    // --------------------------------------------------------------- search
    override suspend fun search(query: String, page: Int): SearchResponseList {
        val slug = slugify(query)
        if (slug.isBlank()) return newSearchResponseList(emptyList(), false)

        val url = if (page <= 1) {
            "$mainUrl/search/$slug/"
        } else {
            "$mainUrl/search/$slug/?mode=ajax&block_id=$BLOCK_SEARCH&from=$page"
        }

        val doc = app.get(url, headers = pageHeaders(), timeout = 30).document
        val items = parseCards(doc)
        return newSearchResponseList(items, items.size >= PAGE_SIZE)
    }

    // ----------------------------------------------------------------- load
    override suspend fun load(url: String): LoadResponse {
        val res = app.get(url, headers = pageHeaders(), timeout = 30)
        val doc = res.document
        val html = res.text

        val title = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: url.trimEnd('/').substringAfterLast('/').replace('-', ' ')

        // og:image is the full preview screenshot (landscape).
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?: doc.selectFirst("meta[name=twitter:image]")?.attr("content")

        val plot = doc.selectFirst("meta[name=description]")?.attr("content")?.trim()
            ?: doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        // flashvars.duration is in seconds.
        val durationSec = Regex("""duration:\s*'(\d+)'""")
            .find(html)?.groupValues?.get(1)?.toIntOrNull()

        val videoId = videoIdOf(url).orEmpty()

        return newMovieLoadResponse(title, url, TvType.Others, url) {
            this.posterUrl = poster
            this.backgroundPosterUrl = poster
            this.plot = plot
            this.duration = durationSec?.let { it / 60 }
            this.recommendations = fetchRecommendations(doc, url, videoId)
        }
    }

    // ------------------------------------------------------ recommendations
    // Combined from all three sources the site offers, in this order:
    //   1. "Related Videos" (already present on the video page)
    //   2. "More Videos with {model}" (ajax tab of the related block)
    //   3. Player related feed (/related_videos_html/{id}/)
    // Duplicates are removed by video id.
    private suspend fun fetchRecommendations(
        doc: Document,
        pageUrl: String,
        videoId: String
    ): List<SearchResponse> {
        val seen = HashSet<String>()
        val result = mutableListOf<SearchResponse>()

        fun addAll(items: List<SearchResponse>) {
            for (item in items) {
                val id = videoIdOf(item.url) ?: item.url
                if (seen.add(id)) result.add(item)
            }
        }

        // 1. Related Videos block (part of the page itself, no extra request).
        doc.selectFirst(".related-videos")?.let { block ->
            addAll(parseCards(block))
        }

        // 2. "More Videos with ..." tab. The mode value lives in
        //    data-parameters="mode_related:X" on the tab link.
        runCatching {
            val mode = doc.selectFirst("a[data-parameters*=mode_related]")
                ?.attr("data-parameters")
                ?.substringAfter("mode_related:")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "4"

            val base = pageUrl.substringBefore('?').trimEnd('/')
            val moreUrl = "$base/?mode=ajax&block_id=$BLOCK_RELATED&mode_related=$mode"
            val moreDoc = app.get(moreUrl, headers = pageHeaders(pageUrl), timeout = 30).document
            addAll(parseCards(moreDoc))
        }

        // 3. Lightweight player feed: ~20 items with a different markup
        //    (thumbnail inside a style="background: url('...')" attribute).
        if (videoId.isNotEmpty()) {
            runCatching {
                val frag = app.get(
                    "$mainUrl/related_videos_html/$videoId/",
                    headers = pageHeaders(pageUrl),
                    timeout = 30
                ).document

                addAll(frag.select("a.player-related-videos-item").mapNotNull { a ->
                    val href = a.attr("href").ifBlank { return@mapNotNull null }
                    val url2 = if (href.startsWith("http")) href else "$mainUrl$href"
                    val title2 = a.attr("title").ifBlank {
                        a.selectFirst("span.title")?.text()
                    }?.trim().orEmpty()
                    if (title2.isBlank()) return@mapNotNull null

                    val style = a.selectFirst("div.thumb")?.attr("style").orEmpty()
                    val thumb = Regex("""url\('([^']+)'\)""").find(style)?.groupValues?.get(1)

                    newMovieSearchResponse(title2, url2, TvType.Others) {
                        this.posterUrl = thumb
                    }
                })
            }
        }

        return result
    }

    // ------------------------------------------------------------ loadLinks
    // No third party embeds: the player plays a direct mp4 from
    // /get_file/... which 302-redirects to the CDN (remote_control.php).
    // The url must be parsed fresh on every play because it is tokenized
    // and expires. No referer is strictly required, but it is sent anyway.
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (!data.startsWith("http")) return false

        val html = try {
            app.get(data, headers = pageHeaders(mainUrl), timeout = 30).text
        } catch (_: Exception) {
            return false
        }

        val videoUrl = Regex("""video_url:\s*'([^']+)'""")
            .find(html)?.groupValues?.get(1)
            ?.ifBlank { null }
            ?: return false

        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = videoUrl,
                type = INFER_TYPE
            ) {
                this.referer = "$mainUrl/"
                this.quality = Qualities.P720.value
            }
        )
        return true
    }
}

// ---------------------------------------------------------------------------
// Plugin entry point. No extractors are needed - the site serves direct mp4.
// ---------------------------------------------------------------------------

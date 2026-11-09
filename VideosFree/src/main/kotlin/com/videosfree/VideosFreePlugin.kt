package com.videosfree

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class VideosFreePlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(VideosFreeProvider())
    }
}

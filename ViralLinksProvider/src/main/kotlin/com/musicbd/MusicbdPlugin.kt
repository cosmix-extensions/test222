package com.musicbd
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin


@CloudstreamPlugin
class MusicbdPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(MusicbdProvider())
    }
}





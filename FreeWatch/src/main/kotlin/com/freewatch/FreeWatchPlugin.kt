package com.freewatch

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class FreeWatchPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(FreeWatchProvider())
        registerExtractorAPI(Vixeo())
        registerExtractorAPI(Luvstream())
        registerExtractorAPI(Firestream())
        registerExtractorAPI(Frstream())
    }
}

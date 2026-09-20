package com.fullporn

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class FullPornPlugin: BasePlugin() {
    override fun load() {
        // Register the provider for FullTo extension
        registerMainAPI(FullPornProvider())
    }
}

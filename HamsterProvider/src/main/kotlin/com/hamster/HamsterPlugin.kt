package com.hamster

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import android.content.Context

@CloudstreamPlugin
class HamsterPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(HamsterProvider())
    }
}





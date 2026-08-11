package com.hamster

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPluginAnnotation
import android.content.Context

@CloudstreamPluginAnnotation
class HamsterPlugin : CloudstreamPlugin() {
    override fun load() {
        registerCsxApi(HamsterProvider())
    }
}

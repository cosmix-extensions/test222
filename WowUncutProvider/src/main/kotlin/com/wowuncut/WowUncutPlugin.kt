package com.wowuncut
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPluginAnnotation


@CloudstreamPluginAnnotation
class WowUncutPlugin : CloudstreamPlugin() {
    override fun load() {
        registerCsxApi(WowUncutProvider())
    }
}

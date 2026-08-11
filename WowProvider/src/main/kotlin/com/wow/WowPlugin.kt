package com.wow
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPluginAnnotation


@CloudstreamPluginAnnotation
class WowPlugin : CloudstreamPlugin() {
    override fun load() {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerCsxApi(WowProvider())
    }
}

package com.musicbd
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPluginAnnotation


@CloudstreamPluginAnnotation
class MusicbdPlugin : CloudstreamPlugin() {
    override fun load() {
        registerCsxApi(MusicbdProvider())
    }
}

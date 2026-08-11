package com.wowuncut
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin


@CloudstreamPlugin
class WowUncutPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(WowUncutProvider())
    }
}





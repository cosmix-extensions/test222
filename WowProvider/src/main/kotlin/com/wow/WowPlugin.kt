package com.wow

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class WowPlugin : Plugin() {
    override fun load(context: Context) {
        val provider = WowProvider()
        registerMainAPI(provider)
        openSettings = { ctx ->
            SettingsDialog(ctx, provider.name, provider.mainUrl).show()
        }
    }
}

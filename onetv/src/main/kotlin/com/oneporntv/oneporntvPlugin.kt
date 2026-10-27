package com.oneporntv

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class OnePornTvPlugin : Plugin() {
    override fun load(context: Context) {
        val provider = OnePornTvProvider()
        registerMainAPI(provider)
        openSettings = { ctx ->
            SettingsDialog(ctx, provider.name, provider.mainUrl).show()
        }
    }
}

package com.vipporns

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

// Entry point of the Vipporns plugin.
// Only the main provider is registered - the site serves direct mp4 files,
// so no ExtractorApi implementations are required.
@CloudstreamPlugin
class VippornsPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(VippornsProvider())
    }
}

package com.wow

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class WowPlugin : Plugin() {
    companion object {
        var pluginContext: Context? = null
        
        private val prefs get() = pluginContext?.getSharedPreferences("WowProviderPrefs", Context.MODE_PRIVATE)

        var cfCookies: String?
            get() = prefs?.getString("WOW_CF_COOKIES", null)
            set(value) { prefs?.edit()?.putString("WOW_CF_COOKIES", value)?.apply() }
            
        var cfUserAgent: String?
            get() = prefs?.getString("WOW_CF_USER_AGENT", null)
            set(value) { prefs?.edit()?.putString("WOW_CF_USER_AGENT", value)?.apply() }
            
        var cfCookieHost: String?
            get() = prefs?.getString("WOW_CF_COOKIE_HOST", "www.wowxxx.to")
            set(value) { prefs?.edit()?.putString("WOW_CF_COOKIE_HOST", value)?.apply() }
            
        var cfWebviewEnabled: Boolean
            get() = prefs?.getBoolean("WOW_CF_WEBVIEW_ENABLED", false) ?: false
            set(value) { prefs?.edit()?.putBoolean("WOW_CF_WEBVIEW_ENABLED", value)?.apply() }
    }

    override fun load(context: Context) {
        pluginContext = context
        // All providers should be added in this manner.
        registerMainAPI(WowProvider())
    }
}

package com.wow

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@CloudstreamPlugin
class WowPlugin : Plugin() {

    var activity: AppCompatActivity? = null

    override fun load(context: Context) {
        activity = context as? AppCompatActivity
        pluginContext = context
        registerMainAPI(WowProvider(this))

        openSettings = {
            val act = activity ?: return@openSettings
            val frag = WowSettingsFragment(this)
            frag.show(act.supportFragmentManager, "WowSettings")
        }
    }

    companion object {
        var pluginContext: Context? = null

        private val prefs
            get() = pluginContext?.getSharedPreferences("WowProviderPrefs", Context.MODE_PRIVATE)

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
}

class WowSettingsFragment(private val plugin: WowPlugin) : BottomSheetDialogFragment() {

    @SuppressLint("DiscouragedApi")
    private fun resId(name: String, type: String): Int? =
        plugin.resources?.getIdentifier(name, type, "com.wow")

    @SuppressLint("DiscouragedApi")
    private fun <T : View> View.findByName(name: String): T? {
        val id = resId(name, "id") ?: return null
        @Suppress("UNCHECKED_CAST")
        return findViewById<T>(id)
    }

    @SuppressLint("DiscouragedApi")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val id = resId("bottom_sheet_layout", "layout") ?: return null
        return plugin.resources?.getLayout(id)?.let {
            inflater.inflate(it, container, false)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title: TextView? = view.findByName("title")
        val description: TextView? = view.findByName("description")
        val autoBypass: Switch? = view.findByName("auto_bypass")
        val bypassButton: Button? = view.findByName("bypass_button")
        val clearButton: Button? = view.findByName("clear_button")

        title?.text = "Cloudflare Protection"
        description?.text =
            "If Wow shows a \"Just a moment\" screen, turn on Auto Bypass and reload, " +
            "or tap \"Bypass Cloudflare\" to open a WebView and solve the challenge."

        autoBypass?.isChecked = WowPlugin.cfWebviewEnabled
        autoBypass?.setOnCheckedChangeListener { _, checked ->
            WowPlugin.cfWebviewEnabled = checked
        }

        bypassButton?.setOnClickListener {
            val act = plugin.activity ?: return@setOnClickListener
            val dialog = CloudflareWebViewDialog("https://www.wowxxx.to/") { _ -> }
            dialog.show(act.supportFragmentManager, "WowCFBypass")
            dismiss()
        }

        clearButton?.setOnClickListener {
            WowPlugin.cfCookies = null
            WowPlugin.cfUserAgent = null
            WowPlugin.cfCookieHost = "www.wowxxx.to"
            autoBypass?.isChecked = false
            Toast.makeText(plugin.activity, "Cookies Cleared", Toast.LENGTH_SHORT).show()
        }
    }
}

class CloudflareWebViewDialog(
    private val targetUrl: String,
    private val onFinished: (Boolean) -> Unit
) : BottomSheetDialogFragment() {

    private var webView: WebView? = null
    private var statusText: TextView? = null
    private var progressBar: ProgressBar? = null

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollStartMs = 0L
    private val pollIntervalMs = 2000L
    private val pollTimeoutMs = 120_000L
    private var cookiesSaved = false
    private var finished = false

    private val challengeTitles = listOf(
        "just a moment", "just a moment...",
        "checking your browser", "attention required",
        "ddos-guard", "one more step"
    )

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (finished) return
            val elapsed = System.currentTimeMillis() - pollStartMs
            if (elapsed > pollTimeoutMs) {
                statusText?.text = "Timed out. Please try again."
                return
            }
            val cookies = CookieManager.getInstance().getCookie(targetUrl).orEmpty()
            if (cookies.isNotBlank() && cookies.contains("cf_clearance")) {
                persistCookies(cookies)
                return
            }
            val title = webView?.title?.lowercase().orEmpty()
            val stillChallenge = challengeTitles.any { title.contains(it) }
            statusText?.text = if (stillChallenge) "Solving Cloudflare challenge..." else "Verifying..."
            handler.postDelayed(this, pollIntervalMs)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val pad = (24 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        statusText = TextView(ctx).apply {
            text = "Opening browser challenge..."
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        progressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
        }
        webView = WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.userAgentString =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (320 * resources.displayMetrics.density).toInt()
            )
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    statusText?.text = "Verifying..."
                    pollRunnable.run()
                }
            }
        }

        val closeBtn = Button(ctx).apply {
            text = "Close"
            setOnClickListener { finishWith(cookiesSaved) }
        }

        root.addView(statusText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(progressBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = pad / 2 })
        root.addView(webView)
        root.addView(closeBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = pad })

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pollStartMs = System.currentTimeMillis()
        handler.postDelayed(pollRunnable, pollIntervalMs)
        webView?.loadUrl(targetUrl)
    }

    private fun persistCookies(cookies: String) {
        if (cookiesSaved) return
        cookiesSaved = true

        scope.launch {
            WowPlugin.cfCookies = cookies
            if (WowPlugin.cfUserAgent.isNullOrBlank()) {
                WowPlugin.cfUserAgent =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
            }
            if (WowPlugin.cfCookieHost.isNullOrBlank()) {
                WowPlugin.cfCookieHost = "www.wowxxx.to"
            }
        }

        statusText?.text = "Cookies saved!"
        progressBar?.visibility = View.GONE
        handler.postDelayed({ finishWith(true) }, 600)
    }

    private fun finishWith(success: Boolean) {
        if (finished) return
        finished = true
        try { dismissAllowingStateLoss() } catch (_: Exception) {}
        onFinished(success)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        handler.removeCallbacks(pollRunnable)
        if (!finished) {
            finished = true
            onFinished(cookiesSaved)
        }
        webView?.let {
            it.stopLoading()
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        webView = null
    }
}

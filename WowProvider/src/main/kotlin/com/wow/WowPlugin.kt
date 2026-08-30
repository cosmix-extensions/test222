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
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.utils.DataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@CloudstreamPlugin
class WowPlugin : BasePlugin() {
    companion object {
        var cfCookies: String?
            get() = DataStore.getKey("WOW_CF_COOKIES")
            set(value) { DataStore.setKey("WOW_CF_COOKIES", value) }
            
        var cfUserAgent: String?
            get() = DataStore.getKey("WOW_CF_USER_AGENT")
            set(value) { DataStore.setKey("WOW_CF_USER_AGENT", value) }
            
        var cfCookieHost: String?
            get() = DataStore.getKey("WOW_CF_COOKIE_HOST") ?: "www.wowxxx.to"
            set(value) { DataStore.setKey("WOW_CF_COOKIE_HOST", value) }
            
        var cfWebviewEnabled: Boolean
            get() = DataStore.getKey("WOW_CF_WEBVIEW_ENABLED") ?: false
            set(value) { DataStore.setKey("WOW_CF_WEBVIEW_ENABLED", value) }
    }

    override fun load(context: Context) {
        registerMainAPI(WowProvider())
        this.settings = WowSettingsFragment()
    }
}

class WowSettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val id = resources.getIdentifier("bottom_sheet_layout", "layout", context?.packageName)
        return if (id != 0) inflater.inflate(id, container, false) else null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val title = view.findViewWithTag<TextView>("title") 
            ?: view.findViewById(resources.getIdentifier("title", "id", context?.packageName))
        val description = view.findViewWithTag<TextView>("description") 
            ?: view.findViewById(resources.getIdentifier("description", "id", context?.packageName))
        val autoBypass = view.findViewWithTag<Switch>("auto_bypass") 
            ?: view.findViewById(resources.getIdentifier("auto_bypass", "id", context?.packageName))
        val bypassButton = view.findViewWithTag<Button>("bypass_button") 
            ?: view.findViewById(resources.getIdentifier("bypass_button", "id", context?.packageName))
        val clearButton = view.findViewWithTag<Button>("clear_button") 
            ?: view.findViewById(resources.getIdentifier("clear_button", "id", context?.packageName))

        title?.text = "Cloudflare Protection"
        description?.text = "If Wow shows a \"Just a moment\" screen, tap below to open a WebView and solve the challenge. Cookies will be saved automatically."
        
        autoBypass?.isChecked = WowPlugin.cfWebviewEnabled
        autoBypass?.setOnCheckedChangeListener { _, checked ->
            WowPlugin.cfWebviewEnabled = checked
        }
        
        bypassButton?.setOnClickListener {
            val activity = activity as? AppCompatActivity ?: return@setOnClickListener
            val dialog = CloudflareWebViewDialog("https://www.wowxxx.to/") { _ -> }
            dialog.show(activity.supportFragmentManager, "WowCFBypass")
        }
        
        clearButton?.setOnClickListener {
            WowPlugin.cfCookies = null
            WowPlugin.cfUserAgent = null
            WowPlugin.cfCookieHost = "www.wowxxx.to"
            autoBypass?.isChecked = false
            Toast.makeText(context, "Cookies Cleared", Toast.LENGTH_SHORT).show()
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
    private var pollStartMs = 0L
    private val pollIntervalMs = 2000L
    private val pollTimeoutMs = 120_000L
    private var cookiesSaved = false
    private var finished = false
    private val challengeTitles = listOf(
        "just a moment",
        "just a moment...",
        "checking your browser",
        "attention required",
        "ddos-guard",
        "one more step"
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
            statusText?.text = if (stillChallenge) "Solving Cloudflare challenge..."
            else "Verifying..."
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
            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
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
        GlobalScope.launch(Dispatchers.IO) {
            WowPlugin.cfCookies = cookies
            if (WowPlugin.cfUserAgent.isNullOrBlank()) {
                WowPlugin.cfUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
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

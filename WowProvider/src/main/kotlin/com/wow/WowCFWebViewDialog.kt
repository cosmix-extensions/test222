package com.wow

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.api.Log

class WowCFWebViewDialog(
    private val targetUrl: String,
    private val onFinished: ((Boolean) -> Unit)? = null
) : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "Wow_CFWebViewDialog"
        private const val POLL_INTERVAL_MS = 1_500L
        private const val POLL_TIMEOUT_MS = 60_000L

        private val CHALLENGE_TITLES = listOf(
            "just a moment",
            "checking your browser",
            "attention required",
            "ddos-guard",
            "one more step"
        )

        fun isChallengeTitle(title: String): Boolean =
            CHALLENGE_TITLES.any { title.lowercase().contains(it) }
    }

    private var webView: WebView? = null
    private var statusText: TextView? = null
    private var progressBar: ProgressBar? = null

    private val handler = Handler(Looper.getMainLooper())
    private var cookiesSaved = false
    private var pollElapsedMs = 0L
    private var pageProgress = 0

    private val targetHost: String by lazy {
        try {
            val uri = android.net.Uri.parse(targetUrl)
            "${uri.scheme}://${uri.host}"
        } catch (_: Exception) {
            targetUrl
        }
    }

    private val cookiePollRunnable = object : Runnable {
        override fun run() {
            if (cookiesSaved || !isAdded) return

            CookieManager.getInstance().flush()
            val cookieStr = CookieManager.getInstance().getCookie(targetUrl) ?: ""
            val title = webView?.title?.lowercase() ?: ""

            Log.d(TAG, "Poll [${pollElapsedMs}ms] Prog: $pageProgress% | Title: '$title' | Cookies: $cookieStr")

            val hasCookies = cookieStr.isNotBlank()
            val isChallenge = isChallengeTitle(title)
            val isValidPage = title.isNotBlank() && title != "about:blank"

            if (hasCookies && !isChallenge && isValidPage && pageProgress == 100) {
                if (cookieStr.contains("__ddg") || cookieStr.contains("cf_clearance") || pollElapsedMs >= 4000L) {
                    saveCookiesAndDismiss(cookieStr)
                    return
                }
            }

            if (pollElapsedMs >= POLL_TIMEOUT_MS) {
                if (hasCookies) {
                    saveCookiesAndDismiss(cookieStr)
                } else {
                    updateStatus("Timed out. Please try again.")
                }
            } else {
                scheduleNextPoll()
            }
        }
    }

    private fun scheduleNextPoll() {
        pollElapsedMs += POLL_INTERVAL_MS
        updateStatus("Checking verification... (${pollElapsedMs / 1000}s)")

        if (pollElapsedMs >= 2000L) {
            (dialog as? BottomSheetDialog)?.behavior?.apply {
                skipCollapsed = true
                peekHeight = android.view.WindowManager.LayoutParams.MATCH_PARENT
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        handler.postDelayed(cookiePollRunnable, POLL_INTERVAL_MS)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_COLLAPSED
            skipCollapsed = false
            peekHeight = 0
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        val bottomSheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )
        bottomSheet?.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
        bottomSheet?.requestLayout()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val screenH = requireContext().resources.displayMetrics.heightPixels
        val webViewHeight = (screenH * 0.70).toInt()

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        root.addView(TextView(requireContext()).apply {
            text = "\uD83D\uDEE1\uFE0F Protection Bypass"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 8)
        })

        statusText = TextView(requireContext()).apply {
            text = "Loading challenge page..."
            textSize = 13f
            setTextColor(Color.parseColor("#A0A0B0"))
            setPadding(0, 0, 0, 4)
        }
        root.addView(statusText)

        root.addView(TextView(requireContext()).apply {
            text = "Please wait while verification completes automatically."
            textSize = 11f
            setTextColor(Color.parseColor("#707080"))
            setPadding(0, 0, 0, 12)
        })

        progressBar = ProgressBar(
            requireContext(), null, android.R.attr.progressBarStyleHorizontal
        ).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 12 }
        }
        root.addView(progressBar)

        val wvContainer = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                webViewHeight
            )
        }
        webView = buildWebView()
        wvContainer.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(wvContainer)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
            flush()
        }

        webView?.loadUrl(targetUrl)
        handler.postDelayed(cookiePollRunnable, POLL_INTERVAL_MS)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(): WebView {
        val wv = WebView(requireContext())

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowContentAccess = true
            allowFileAccess = true
            loadsImagesAutomatically = true
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                pageProgress = newProgress
                if (!cookiesSaved) {
                    updateStatus("Loading... $newProgress%")
                }
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?, request: WebResourceRequest?
            ): Boolean = false
        }

        return wv
    }

    private fun saveCookiesAndDismiss(cookieStr: String) {
        if (cookiesSaved) return
        cookiesSaved = true

        handler.removeCallbacks(cookiePollRunnable)

        WowPlugin.cfCookies = cookieStr
        WowPlugin.cfCookieHost = "www.wowxxx.to"
        webView?.settings?.userAgentString?.let { ua ->
            WowPlugin.cfUserAgent = ua
        }

        Log.d(TAG, "Saved cookies: $cookieStr")
        updateStatus("\u2705 Done! Cookies saved.")

        webView?.postDelayed({
            if (isAdded) {
                onFinished?.invoke(true)
                dismissAllowingStateLoss()
            }
        }, 1000)
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        if (!cookiesSaved) {
            handler.removeCallbacks(cookiePollRunnable)
            onFinished?.invoke(false)
        }
    }

    private fun updateStatus(msg: String) {
        activity?.runOnUiThread {
            statusText?.text = msg
            if (msg.startsWith("\u2705")) {
                progressBar?.visibility = View.GONE
                statusText?.setTextColor(Color.parseColor("#4CAF50"))
            } else {
                progressBar?.visibility = View.VISIBLE
                statusText?.setTextColor(Color.parseColor("#A0A0B0"))
            }
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacks(cookiePollRunnable)
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
        super.onDestroyView()
    }
}

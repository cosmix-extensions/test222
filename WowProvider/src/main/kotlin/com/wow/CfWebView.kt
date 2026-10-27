package com.wow

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.nicehttp.NiceResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Saved WebView result (cookies + the exact User-Agent that solved the challenge). */
object CfStore {
    private const val P = "wow"

    var cookies: String
        get() = getKey<String>("${P}_cf_cookies") ?: ""
        set(v) { setKey("${P}_cf_cookies", v) }

    var userAgent: String
        get() = getKey<String>("${P}_cf_ua") ?: ""
        set(v) { setKey("${P}_cf_ua", v) }

    /** Open the WebView automatically when the home page is blocked (default on). */
    var autoWebView: Boolean
        get() = getKey<Boolean>("${P}_auto_webview") ?: true
        set(v) { setKey("${P}_auto_webview", v) }

    fun clear(url: String) {
        cookies = ""
        userAgent = ""
        runCatching {
            val cm = CookieManager.getInstance()
            val host = Uri.parse(url).host ?: return@runCatching
            cm.getCookie(url)?.split(";")?.forEach { c ->
                val name = c.substringBefore("=").trim()
                if (name.isNotEmpty()) {
                    cm.setCookie(url, "$name=; Max-Age=0; Path=/")
                    cm.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=.${host.removePrefix("www.")}")
                }
            }
            cm.flush()
        }
    }
}

/**
 * Opens the WebView at most ONCE per app session for automatic use.
 * Every home-page section that gets blocked waits on the same mutex: the first one opens the
 * WebView, the others reuse its cookies. If it was closed without success, nothing reopens it
 * until the app restarts (or the user taps "Open WebView" in settings).
 */
object CfBypass {
    private val mutex = Mutex()
    @Volatile private var autoTried = false

    private val markers = listOf("just a moment", "cf-chl", "challenge-platform", "attention required", "checking your browser")

    fun isBlocked(r: NiceResponse): Boolean {
        if (r.code != 403 && r.code != 503) return false
        val t = r.text.lowercase()
        return markers.any { t.contains(it) }
    }

    fun isChallengeTitle(title: String?): Boolean {
        val t = title?.lowercase() ?: return true
        return t.isBlank() || t.contains("just a moment") || t.contains("attention required") ||
            t.contains("checking your browser") || t.contains("one more step")
    }

    /** @param cookiesBefore the cookies the blocked request was sent with */
    suspend fun solveOnce(url: String, cookiesBefore: String): Boolean = mutex.withLock {
        if (CfStore.cookies.isNotBlank() && CfStore.cookies != cookiesBefore) return@withLock true
        if (autoTried || !CfStore.autoWebView) return@withLock false
        autoTried = true
        showAndWait(url)
    }

    suspend fun showAndWait(url: String): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val activity = CommonActivity.activity
            if (activity == null || activity.isFinishing) {
                cont.resume(false); return@suspendCancellableCoroutine
            }
            var done = false
            val dialog = CfWebViewDialog(activity, url) { ok ->
                if (!done) { done = true; cont.resume(ok) }
            }
            cont.invokeOnCancellation { activity.runOnUiThread { runCatching { dialog.dismiss() } } }
            dialog.show()
        }
    }
}

/** Full-screen WebView with a dark top bar: back, URL, ⋮ (Open in browser / Copy link). No XML. */
@SuppressLint("SetJavaScriptEnabled")
class CfWebViewDialog(
    private val activity: Activity,
    private val startUrl: String,
    private val onFinished: (Boolean) -> Unit,
) : Dialog(activity, android.R.style.Theme_Black_NoTitleBar) {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var webView: WebView
    private lateinit var urlText: TextView
    private lateinit var progress: ProgressBar
    private var saved = false
    private var progressNow = 0
    private var loadedSince = 0L

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()

    private fun circle(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

    private fun iconButton(icon: Drawable, onClick: (View) -> Unit) = ImageView(context).apply {
        setImageDrawable(icon)
        background = circle(Color.parseColor("#1C1C1E"))
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(14), dp(14), dp(14), dp(14))
        isClickable = true
        isFocusable = true
        setOnClickListener(onClick)
    }

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        // Top bar
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Color.BLACK)
        }
        bar.addView(iconButton(ChevronIcon(dp(2).toFloat())) { goBackOrClose() }, LinearLayout.LayoutParams(dp(50), dp(50)))
        urlText = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            text = prettyUrl(startUrl)
        }
        bar.addView(urlText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(14); marginEnd = dp(14)
        })
        bar.addView(iconButton(DotsIcon()) { showMenu(it) }, LinearLayout.LayoutParams(dp(50), dp(50)))
        root.addView(bar)

        progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressDrawable?.setTint(Color.parseColor("#4F8CFF"))
        }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))

        webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // Use the WebView's own UA without the "wv" marker, and save it with the cookies
            settings.userAgentString = settings.userAgentString
                .replace("; wv)", ")").replace(" Version/4.0", "")
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressNow = newProgress
                    progress.progress = newProgress
                    progress.visibility = if (newProgress >= 100) View.INVISIBLE else View.VISIBLE
                    if (newProgress >= 100 && loadedSince == 0L) loadedSince = System.currentTimeMillis()
                    if (newProgress < 100) loadedSince = 0L
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    url?.let { urlText.text = prettyUrl(it) }
                }
            }
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        setCancelable(true)
        setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) { goBackOrClose(); true }
            else keyCode == KeyEvent.KEYCODE_BACK
        }
        setOnDismissListener {
            handler.removeCallbacksAndMessages(null)
            runCatching { webView.stopLoading(); webView.destroy() }
            onFinished(saved)
        }
    }

    override fun show() {
        super.show()
        window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.BLACK))
        }
        webView.loadUrl(startUrl)
        handler.postDelayed(poll, 1000)
    }

    /** Closes by itself once the challenge is passed and cookies are stored. */
    private val poll = object : Runnable {
        override fun run() {
            if (saved || !isShowing) return
            val cookies = CookieManager.getInstance().getCookie(startUrl) ?: ""
            val okPage = progressNow >= 100 && !CfBypass.isChallengeTitle(webView.title)
            val stable = loadedSince > 0 && System.currentTimeMillis() - loadedSince > 2500
            if (okPage && cookies.isNotBlank() && (cookies.contains("cf_clearance") || stable)) {
                save(cookies)
                return
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun save(cookies: String) {
        saved = true
        CookieManager.getInstance().flush()
        CfStore.cookies = cookies
        CfStore.userAgent = webView.settings.userAgentString ?: ""
        Toast.makeText(context, "Verified ✓", Toast.LENGTH_SHORT).show()
        handler.postDelayed({ runCatching { dismiss() } }, 700)
    }

    private fun goBackOrClose() {
        if (webView.canGoBack()) webView.goBack() else dismiss()
    }

    private fun currentUrl() = webView.url ?: startUrl

    private fun prettyUrl(u: String) = u.substringAfter("://").removePrefix("www.").trimEnd('/')

    private fun showMenu(anchor: View) {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(26).toFloat()
                setColor(Color.parseColor("#111111"))
            }
            elevation = dp(12).toFloat()
        }
        lateinit var popup: PopupWindow
        fun item(icon: Drawable, label: String, action: () -> Unit) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(16), dp(28), dp(16))
                isClickable = true
                background = GradientDrawable().apply {
                    cornerRadius = dp(18).toFloat(); setColor(Color.TRANSPARENT)
                }
                setOnClickListener { popup.dismiss(); action() }
            }
            row.addView(ImageView(context).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(dp(26), dp(26)))
            row.addView(TextView(context).apply {
                text = label
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(18) })
            box.addView(row)
        }
        item(ExternalIcon(dp(2).toFloat()), "Open in browser") {
            runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl()))) }
        }
        item(LinkIcon(dp(2).toFloat()), "Copy link") {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("link", currentUrl()))
            Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
        }
        popup = PopupWindow(box, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = dp(12).toFloat()
            isOutsideTouchable = true
        }
        popup.showAsDropDown(anchor, 0, dp(4), Gravity.END)
    }
}

/* ---------- Icons drawn in code (no XML) ---------- */

abstract class LineIcon(stroke: Float) : Drawable() {
    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    protected fun x(f: Float) = bounds.left + bounds.width() * f
    protected fun y(f: Float) = bounds.top + bounds.height() * f
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

class ChevronIcon(stroke: Float) : LineIcon(stroke * 1.2f) {
    override fun draw(canvas: Canvas) {
        canvas.drawPath(Path().apply {
            moveTo(x(0.62f), y(0.22f)); lineTo(x(0.36f), y(0.5f)); lineTo(x(0.62f), y(0.78f))
        }, paint)
    }
}

class DotsIcon : LineIcon(0f) {
    init { paint.style = Paint.Style.FILL }
    override fun draw(canvas: Canvas) {
        val r = bounds.width() * 0.07f
        listOf(0.22f, 0.5f, 0.78f).forEach { canvas.drawCircle(x(0.5f), y(it), r, paint) }
    }
}

class ExternalIcon(stroke: Float) : LineIcon(stroke) {
    override fun draw(canvas: Canvas) {
        canvas.drawPath(Path().apply {
            moveTo(x(0.45f), y(0.16f)); lineTo(x(0.24f), y(0.16f))
            quadTo(x(0.14f), y(0.16f), x(0.14f), y(0.26f))
            lineTo(x(0.14f), y(0.76f)); quadTo(x(0.14f), y(0.86f), x(0.24f), y(0.86f))
            lineTo(x(0.74f), y(0.86f)); quadTo(x(0.84f), y(0.86f), x(0.84f), y(0.76f))
            lineTo(x(0.84f), y(0.55f))
            moveTo(x(0.46f), y(0.54f)); lineTo(x(0.86f), y(0.14f))
            moveTo(x(0.62f), y(0.14f)); lineTo(x(0.86f), y(0.14f)); lineTo(x(0.86f), y(0.38f))
        }, paint)
    }
}

class LinkIcon(stroke: Float) : LineIcon(stroke) {
    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.rotate(-45f, x(0.5f), y(0.5f))
        val r = bounds.height() * 0.13f
        canvas.drawRoundRect(RectF(x(0.06f), y(0.37f), x(0.54f), y(0.63f)), r, r, paint)
        canvas.drawRoundRect(RectF(x(0.46f), y(0.37f), x(0.94f), y(0.63f)), r, r, paint)
        canvas.restore()
    }
}

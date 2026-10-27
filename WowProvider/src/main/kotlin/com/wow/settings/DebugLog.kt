package com.wow.settings

// TEMPORARY debug log (remove later). Self-contained: only SettingsDialog adds one button for it,
// and WowProvider calls DebugLog.d(...). Deleting this file + those lines removes it fully.

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLog {
    private const val TAG = "WowDebug"
    private const val MAX = 400
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** Posters returned by load(): url -> headers snapshot taken at load() time. */
    private val posters = LinkedHashMap<String, Map<String, String>>()

    @Synchronized
    fun d(tag: String, msg: String) {
        val line = "${fmt.format(Date())} [$tag] $msg"
        lines.addLast(line)
        while (lines.size > MAX) lines.removeFirst()
        try { Log.d(TAG, line) } catch (_: Throwable) {}
    }

    @Synchronized
    fun addPoster(url: String?, headers: Map<String, String>) {
        if (url.isNullOrBlank()) return
        posters.remove(url)
        posters[url] = HashMap(headers)
        while (posters.size > 15) posters.remove(posters.keys.first())
    }

    @Synchronized fun text(): String = lines.joinToString("\n")
    @Synchronized fun clear() { lines.clear(); posters.clear() }
    @Synchronized fun postersSnapshot(): List<Pair<String, Map<String, String>>> = posters.map { it.key to it.value }

    /** Short, safe description of headers (cookie value not shown in full). */
    fun describe(h: Map<String, String>): String {
        val c = h["Cookie"].orEmpty()
        return "UA=" + (h["User-Agent"]?.take(40) ?: "none") +
            " | Referer=" + (h["Referer"] ?: "none") +
            " | Cookie=" + (if (c.isBlank()) "NONE" else "${c.length} chars, cf_clearance=${c.contains("cf_clearance")}")
    }

    /** Re-downloads the last load() posters with their headers and logs the HTTP result. */
    suspend fun testPosters() {
        val list = postersSnapshot()
        if (list.isEmpty()) { d("TEST", "no posters yet - open the home page first"); return }
        for ((url, h) in list) {
            try {
                val r = app.get(url, headers = h, timeout = 30)
                val type = r.headers["Content-Type"] ?: r.headers["content-type"] ?: "?"
                val size = r.body.bytes().size
                d("TEST", "${r.code} type=$type bytes=$size <- $url")
            } catch (e: Throwable) {
                d("TEST", "ERROR ${e.javaClass.simpleName}: ${e.message} <- $url")
            }
        }
    }
}

/** Simple log viewer: Test posters, Copy, Clear, Close. */
class DebugLogDialog(context: Context) : Dialog(context) {
    private val accent = Color.parseColor("#4F8CFF")
    private lateinit var body: TextView

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()

    private fun small(label: String, onClick: () -> Unit) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(accent)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(8), dp(10), dp(8), dp(10))
        isClickable = true
        setOnClickListener { onClick() }
    }

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(10))
            background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(Color.parseColor("#1C1C1E")) }
        }
        root.addView(TextView(context).apply {
            text = "Debug log"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
        })
        body = TextView(context).apply {
            setTextColor(Color.parseColor("#D0D0D5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(context).apply { addView(body) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            (context.resources.displayMetrics.heightPixels * 0.6).toInt()).apply { topMargin = dp(10) })

        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(small("Test posters") {
            Toast.makeText(context, "Testing...", Toast.LENGTH_SHORT).show()
            CoroutineScope(Dispatchers.IO).launch {
                DebugLog.testPosters()
                withContext(Dispatchers.Main) { refresh(); scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) } }
            }
        }, lp)
        row.addView(small("Refresh") { refresh() }, lp)
        row.addView(small("Copy") {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("log", DebugLog.text()))
            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        }, lp)
        row.addView(small("Clear") { DebugLog.clear(); refresh() }, lp)
        row.addView(small("Close") { dismiss() }, lp)
        root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })

        setContentView(root)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        refresh()
    }

    private fun refresh() { body.text = DebugLog.text().ifEmpty { "(empty)" } }

    override fun show() {
        super.show()
        window?.setLayout((context.resources.displayMetrics.widthPixels * 0.95).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}

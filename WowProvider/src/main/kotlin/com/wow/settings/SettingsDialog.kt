package com.wow.settings

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.lagradost.cloudstream3.CommonActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Plugin settings: auto WebView switch, manual "Open WebView", clear cookies. Built in code, no XML. */
class SettingsDialog(context: Context, private val siteName: String, private val siteUrl: String) : Dialog(context) {
    private val accent = Color.parseColor("#4F8CFF")
    private lateinit var status: TextView

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()

    private fun round(color: Int, radius: Int, strokeColor: Int? = null) = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(color)
        if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = s
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun button(label: String, filled: Boolean, onClick: () -> Unit) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(if (filled) Color.WHITE else accent)
        background = if (filled) round(accent, 16) else round(Color.TRANSPARENT, 16, Color.parseColor("#3A3A3C"))
        setPadding(dp(16), dp(14), dp(16), dp(14))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(18))
            background = round(Color.parseColor("#1C1C1E"), 26)
        }
        root.addView(text(siteName.uppercase(), 12f, Color.parseColor("#9A9A9A")).apply { letterSpacing = 0.1f })
        root.addView(text("Settings", 22f, Color.WHITE, true))

        // Auto WebView card
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(12), dp(14))
            background = round(Color.parseColor("#2A2A2D"), 18)
        }
        val labels = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(text("Auto WebView", 16f, Color.WHITE, true))
        labels.addView(text("Opens once on the home page if the site is blocked", 12f, Color.parseColor("#A0A0A5")))
        card.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(Switch(context).apply {
            isChecked = CfStore.autoWebView
            thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(accent, Color.parseColor("#C7C7CC")))
            trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Color.parseColor("#2E4F8F"), Color.parseColor("#48484A")))
            setOnCheckedChangeListener { _, on -> CfStore.autoWebView = on }
        })
        root.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18) })

        status = text("", 13f, Color.parseColor("#A0A0A5"))
        root.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14); marginStart = dp(4) })
        refreshStatus()

        root.addView(button("Open WebView", true) {
            CoroutineScope(Dispatchers.Main).launch {
                val ok = CfBypass.showAndWait(siteUrl)
                refreshStatus()
                if (ok) Toast.makeText(context, "Cookies saved", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })

        root.addView(button("Clear cookies", false) {
            CfStore.clear(siteUrl)
            refreshStatus()
            Toast.makeText(context, "Cookies cleared", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })

        root.addView(text("Close", 15f, accent, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(14), dp(16), dp(4))
            isClickable = true
            setOnClickListener { dismiss() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(root)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    override fun show() {
        super.show()
        window?.setLayout((context.resources.displayMetrics.widthPixels * 0.9).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun refreshStatus() {
        val saved = CfStore.cookies.isNotBlank()
        status.text = if (saved) "● Cookies saved" else "● No cookies saved"
        status.setTextColor(if (saved) Color.parseColor("#4CD964") else Color.parseColor("#A0A0A5"))
    }
}

package com.wow

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.plugins.Plugin

class WowSettingsFragment(private val plugin: Plugin) : BottomSheetDialogFragment() {

    private lateinit var bypassBtn: Button
    private lateinit var ctx: Context

    @SuppressLint("SetTextI18n", "UseSwitchCompatOrMaterialCode")
    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        ctx = requireContext()

        val scrollView = ScrollView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val mainLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.TOP
        }

        // Header
        mainLayout.addView(TextView(ctx).apply {
            text = "Wow Provider Settings"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dpToPx(12))
        })

        val divider = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)
            ).apply { bottomMargin = dpToPx(12) }
            setBackgroundColor(Color.parseColor("#333333"))
        }
        mainLayout.addView(divider)

        // Section title
        mainLayout.addView(TextView(ctx).apply {
            text = "Cloudflare Protection Bypass"
            textSize = 17f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dpToPx(8))
        })

        mainLayout.addView(TextView(ctx).apply {
            text = "If the site shows a Cloudflare verification screen, use the bypass below. " +
                    "Auto Bypass will open the WebView once, save cookies, and reuse them for all future requests."
            textSize = 13f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 0, 0, dpToPx(12))
        })

        // Auto bypass toggle
        val switchLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(16) }
            gravity = Gravity.CENTER_VERTICAL
        }

        switchLayout.addView(TextView(ctx).apply {
            text = "Auto WebView Bypass"
            textSize = 15f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        val autoBypassSwitch = Switch(ctx).apply {
            isChecked = getKey("wow_auto_bypass") ?: true
            setOnCheckedChangeListener { _, isChecked ->
                setKey("wow_auto_bypass", isChecked)
            }
        }
        switchLayout.addView(autoBypassSwitch)
        mainLayout.addView(switchLayout)

        // Manual bypass button
        bypassBtn = Button(ctx).apply {
            text = if (WowPlugin.cfCookies.isNotBlank()) {
                "\u2705 Cookies Saved - Refresh"
            } else {
                "\uD83D\uDEE1\uFE0F Bypass Protection"
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(8) }
            setOnClickListener {
                val dialog = WowCFWebViewDialog(
                    targetUrl = "https://www.wowxxx.to",
                    onFinished = { saved ->
                        if (saved) {
                            bypassBtn.text = "\u2705 Cookies Saved - Refresh"
                            Toast.makeText(ctx, "Done! Cookies saved.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                dialog.show(parentFragmentManager, "wow_cf_bypass")
            }
        }
        mainLayout.addView(bypassBtn)

        // Clear cookies button
        val clearBtn = Button(ctx).apply {
            text = "Clear Cookies"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(16) }
            setOnClickListener {
                AlertDialog.Builder(ctx)
                    .setTitle("Clear Cookies?")
                    .setMessage("This will remove all saved cookies. You will need to bypass again on next request.")
                    .setPositiveButton("Clear") { _, _ ->
                        val cm = CookieManager.getInstance()
                        val url = "https://www.wowxxx.to"
                        val domain = "wowxxx.to"

                        val cookieString = cm.getCookie(url)
                        if (cookieString != null) {
                            cookieString.split(";").forEach { cookie ->
                                val cookieName = cookie.substringBefore("=").trim()
                                if (cookieName.isNotBlank()) {
                                    cm.setCookie(url, "$cookieName=; Max-Age=0; expires=Thu, 01 Jan 1970 00:00:00 GMT; domain=$domain; path=/")
                                    cm.setCookie(url, "$cookieName=; Max-Age=0; expires=Thu, 01 Jan 1970 00:00:00 GMT; domain=.$domain; path=/")
                                    cm.setCookie(url, "$cookieName=; Max-Age=0; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/")
                                }
                            }
                        }
                        cm.flush()

                        WowPlugin.cfCookies = ""
                        WowPlugin.cfUserAgent = ""
                        WowPlugin.cfCookieHost = ""

                        bypassBtn.text = "\uD83D\uDEE1\uFE0F Bypass Protection"
                        Toast.makeText(ctx, "Cookies cleared.", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
                    .show()
            }
        }
        mainLayout.addView(clearBtn)

        scrollView.addView(mainLayout)
        return scrollView
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
        return dialog
    }

    private fun dpToPx(dp: Int): Int {
        val scale = ctx.resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }
}

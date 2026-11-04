package com.browsex.app

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/** Native, accessible reading surface for text extracted by GeckoView. */
class ReaderPage(
    private val context: Context,
    private val pageTitle: String,
    private val sourceUrl: String,
    private val articleText: String,
    private val store: BrowserStore
) {
    private val colors get() = BrowseXColors.resolve(context, store)
    private val density = context.resources.displayMetrics.density
    private fun t(en: String, ar: String) = if (BrowseXColors.isArabic(store)) ar else en
    private fun dp(value: Int) = (value * density).toInt()

    fun show() {
        var textSize = 18f
        val background = colors.canvas
        val ink = colors.ink
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(16))
            setBackgroundColor(background)
            BrowseXColors.applyDirection(this, store)
        }
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = TextView(context).apply {
            text = t("BROWSE X  ·  READER", "BrowseX · وضع القراءة")
            textSize = 11f
            letterSpacing = 0.08f
            setTextColor(colors.muted)
            typeface = Typeface.DEFAULT_BOLD
        }
        top.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        lateinit var dialog: Dialog
        val close = TextView(context).apply {
            text = "✕"
            textSize = 19f
            gravity = Gravity.CENTER
            contentDescription = t("Close reader", "إغلاق وضع القراءة")
            setTextColor(ink)
            setPadding(dp(12), dp(6), dp(4), dp(6))
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            setOnClickListener { dialog.dismiss() }
        }
        top.addView(close)
        root.addView(top)

        val title = TextView(context).apply {
            text = pageTitle.ifBlank { t("Article", "مقال") }
            textSize = 27f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(ink)
            setPadding(0, dp(24), 0, dp(9))
        }
        root.addView(title)
        val source = TextView(context).apply {
            text = android.net.Uri.parse(sourceUrl).host.orEmpty()
            textSize = 12f
            setTextColor(colors.muted)
            setPadding(0, 0, 0, dp(22))
        }
        root.addView(source)

        val body = TextView(context).apply {
            text = articleText
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            this.textSize = textSize
            setTextColor(ink)
            setLineSpacing(dp(5).toFloat(), 1.08f)
            setTextIsSelectable(true)
            typeface = Typeface.create("serif", Typeface.NORMAL)
            gravity = Gravity.START
        }
        val scroll = ScrollView(context).apply {
            isFillViewport = false
            addView(body, ViewGroup.LayoutParams(-1, -2))
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        fun control(label: String, description: String, action: () -> Unit): TextView =
            TextView(context).apply {
                text = label
                contentDescription = description
                textSize = 17f
                gravity = Gravity.CENTER
                setTextColor(ink)
                this.background = GradientDrawable().apply {
                    setColor(colors.card)
                    cornerRadius = dp(12).toFloat()
                }
                setOnClickListener { action() }
            }
        controls.addView(control("A−", t("Decrease text size", "تصغير الخط")) {
            textSize = (textSize - 1f).coerceAtLeast(14f)
            body.textSize = textSize
        }, LinearLayout.LayoutParams(dp(52), dp(48)).apply { marginEnd = dp(8) })
        controls.addView(control("A+", t("Increase text size", "تكبير الخط")) {
            textSize = (textSize + 1f).coerceAtMost(30f)
            body.textSize = textSize
        }, LinearLayout.LayoutParams(dp(52), dp(48)).apply { marginEnd = dp(8) })
        controls.addView(control(t("Copy", "نسخ"), t("Copy article text", "نسخ نص المقال")) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(pageTitle, articleText))
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(controls)

        dialog = AlertDialog.Builder(context).setView(root).create()
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }
}

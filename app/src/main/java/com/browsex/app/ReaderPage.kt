package com.browsex.app

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.browsex.app.storage.BrowserSettings

/** Native, accessible reading surface for text extracted by GeckoView. */
class ReaderPage(
    private val context: Context,
    private val pageTitle: String,
    private val sourceUrl: String,
    private val articleText: String,
) {
    private val colors get() = BrowseXColors.resolve(context, (context as? MainActivity)?.currentBrowserSettings ?: BrowserSettings())
    private val isTvRemote get() = WrenAdaptiveUi.isTelevision(context)
    private val density = context.resources.displayMetrics.density
    private fun t(en: String, ar: String) = BrowseXStrings.text(context, en, ar)
    private fun dp(value: Int) = (value * density).toInt()

    fun show() {
        var textSize = if (isTvRemote) 22f else 18f
        val background = colors.canvas
        val ink = colors.ink
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(WrenAdaptiveUi.horizontalSafePadding(context, 22), WrenAdaptiveUi.verticalSafePadding(context, 18), WrenAdaptiveUi.horizontalSafePadding(context, 22), dp(if (isTvRemote) 28 else 16))
            setBackgroundColor(background)
            (context as? MainActivity)?.let { BrowseXColors.applyDirection(this, it.currentBrowserSettings) }
        }
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = TextView(context).apply {
            text = t("WREN BROWSER  ·  READER", "Wren Browser · وضع القراءة")
            textSize = if (isTvRemote) 15f else 11f
            letterSpacing = 0.08f
            setTextColor(colors.muted)
            typeface = Typeface.DEFAULT_BOLD
        }
        top.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        lateinit var dialog: Dialog
        val close = TextView(context).apply {
            text = "✕"
            textSize = if (isTvRemote) 30f else 19f
            gravity = Gravity.CENTER
            contentDescription = t("Close reader", "إغلاق وضع القراءة")
            setTextColor(ink)
            setPadding(dp(12), dp(6), dp(4), dp(6))
            WrenAdaptiveUi.prepareFocusable(this, context, 14, colors.accent, if (isTvRemote) 64 else 48)
            setOnClickListener { dialog.dismiss() }
        }
        top.addView(close)
        root.addView(top)

        val title = TextView(context).apply {
            text = pageTitle.ifBlank { t("Article", "مقال") }
            textSize = if (isTvRemote) 36f else 27f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(ink)
            setPadding(0, dp(24), 0, dp(9))
        }
        root.addView(title)
        val source = TextView(context).apply {
            text = android.net.Uri.parse(sourceUrl).host.orEmpty()
            textSize = if (isTvRemote) 16f else 12f
            setTextColor(colors.muted)
            setPadding(0, 0, 0, dp(22))
        }
        root.addView(source)

        val body = TextView(context).apply {
            text = articleText
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            this.textSize = textSize
            isFocusable = true
            isFocusableInTouchMode = false
            setTextColor(ink)
            setLineSpacing(dp(5).toFloat(), 1.08f)
            setTextIsSelectable(true)
            typeface = Typeface.create("serif", Typeface.NORMAL)
            gravity = Gravity.START
        }
        val scroll = ScrollView(context).apply {
            isFillViewport = false
            isFocusable = true
            isFocusableInTouchMode = false
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
                textSize = if (isTvRemote) 22f else 17f
                gravity = Gravity.CENTER
                setTextColor(ink)
                this.background = GradientDrawable().apply {
                    setColor(this@ReaderPage.colors.card)
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), this@ReaderPage.colors.line)
                }
                WrenAdaptiveUi.prepareFocusable(this, context, 12, colors.accent, if (isTvRemote) 64 else 48)
                setOnClickListener { action() }
            }
        controls.addView(control("A−", t("Decrease text size", "تصغير الخط")) {
            textSize = (textSize - 1f).coerceAtLeast(14f)
            body.textSize = textSize
        }, LinearLayout.LayoutParams(dp(if (isTvRemote) 68 else 52), dp(if (isTvRemote) 64 else 48)).apply { marginEnd = dp(8) })
        controls.addView(control("A+", t("Increase text size", "تكبير الخط")) {
            textSize = (textSize + 1f).coerceAtMost(if (isTvRemote) 36f else 30f)
            body.textSize = textSize
        }, LinearLayout.LayoutParams(dp(52), dp(48)).apply { marginEnd = dp(8) })
        controls.addView(control(t("Copy", "نسخ"), t("Copy article text", "نسخ نص المقال")) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(pageTitle, articleText))
        }, LinearLayout.LayoutParams(0, dp(if (isTvRemote) 64 else 48), 1f))
        root.addView(controls)

        dialog = AlertDialog.Builder(context).setView(root).create()
        dialog.show()
        if (isTvRemote) {
            val safeWidth = (context.resources.displayMetrics.widthPixels - WrenAdaptiveUi.dp(context, 96))
                .coerceAtLeast(WrenAdaptiveUi.dp(context, 560))
            dialog.window?.setLayout(minOf(WrenAdaptiveUi.dp(context, 1500), safeWidth), ViewGroup.LayoutParams.MATCH_PARENT)
            close.post { if (dialog.isShowing) close.requestFocus() }
        } else {
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }
}

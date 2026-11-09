package com.browsex.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.browsex.app.storage.BrowserSettings
import kotlinx.coroutines.launch
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Full-screen settings destination. Browser actions are injected from the activity so
 * this screen never owns GeckoSession or reaches into another feature's UI.
 */
internal class SettingsPage(
    private val activity: MainActivity,
    private val openBookmarks: () -> Unit,
    private val openHistory: () -> Unit,
    private val openDownloads: () -> Unit,
    private val clearHistory: () -> Unit,
    private val setWebsiteNotifications: (Boolean) -> Unit,
    private val importBookmarks: () -> Unit,
    private val exportBookmarks: () -> Unit,
    private val showHomePreferences: () -> Unit,
    private val openPrivacyCenter: () -> Unit,
    private val openSitePermissions: () -> Unit,
    private val manageUBlockOrigin: () -> Unit,
    private val openAutofillSettings: () -> Unit
) {
    private val colors get() = BrowseXColors.resolve(activity, activity.currentBrowserSettings)
    private val isTvRemote get() = WrenAdaptiveUi.isTelevision(activity)
    private val ink get() = colors.ink
    private val muted get() = colors.muted
    private val accent get() = colors.accent
    private val canvas get() = colors.canvas

    private fun t(value: String, fallback: String = value): String = BrowseXStrings.text(activity, value, fallback)


    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), this@SettingsPage.colors.line)
    }

    fun show() {
        activity.lifecycleScope.launch {
            val settings = runCatching { activity.storageRuntime.settings.snapshot() }.getOrElse {
                android.util.Log.e("BrowseX", "Could not load settings page state", it)
                android.widget.Toast.makeText(activity, t("Settings are temporarily unavailable", "الإعدادات غير متاحة مؤقتًا"), android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            show(settings)
        }
    }

    private fun show(settings: BrowserSettings) {
        lateinit var dialog: AlertDialog
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(WrenAdaptiveUi.horizontalSafePadding(activity, if (isTvRemote) 30 else 18), WrenAdaptiveUi.verticalSafePadding(activity, if (isTvRemote) 26 else 16), WrenAdaptiveUi.horizontalSafePadding(activity, if (isTvRemote) 30 else 18), dp(if (isTvRemote) 28 else 16))
            setBackgroundColor(canvas)
            BrowseXColors.applyDirection(this, activity.currentBrowserSettings)
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(TextView(activity).apply {
            text = t("Settings")
            textSize = if (isTvRemote) 34f else 27f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ink)
        })
        heading.addView(TextView(activity).apply {
            text = t("Make Browse X work the way you like")
            textSize = if (isTvRemote) 15f else 12f
            setTextColor(muted)
            setPadding(0, dp(3), 0, 0)
        })
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        val close = TextView(activity).apply {
            text = "×"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(ink)
            background = rounded(colors.card, 14)
            foreground = WrenFocusStyle.overlay(activity, 14, accent)
            isFocusable = true
            isFocusableInTouchMode = false
            contentDescription = t("Close settings")
        }
        val closeSize = dp(if (isTvRemote) 60 else 48)
        close.minimumWidth = closeSize
        close.minimumHeight = closeSize
        header.addView(close, LinearLayout.LayoutParams(closeSize, closeSize))
        page.addView(header)

        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, dp(18), 0, dp(8))
        }
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)

        fun section(title: String, subtitle: String? = null) {
            content.addView(TextView(activity).apply {
                text = t(title).uppercase()
                textSize = if (isTvRemote) 14f else 11f
                letterSpacing = .08f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(muted)
                setPadding(dp(3), dp(17), dp(3), dp(8))
            })
            if (subtitle != null) content.addView(TextView(activity).apply {
                text = t(subtitle)
                textSize = 12f
                setTextColor(muted)
                setPadding(dp(3), 0, dp(3), dp(8))
            })
        }

        fun row(title: String, description: String, trailing: String = "›", action: () -> Unit) {
            val item = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(if (isTvRemote) 20 else 14), dp(if (isTvRemote) 18 else 13), dp(if (isTvRemote) 18 else 12), dp(if (isTvRemote) 18 else 13))
                minimumHeight = dp(if (isTvRemote) 76 else 58)
                background = rounded(colors.card, 15)
                foreground = WrenFocusStyle.overlay(activity, 15, accent)
                isClickable = true
                isFocusable = true
                isFocusableInTouchMode = false
                setOnClickListener { action() }
            }
            val labels = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(TextView(activity).apply {
                text = t(title)
                textSize = if (isTvRemote) 18f else 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ink)
            })
            labels.addView(TextView(activity).apply {
                text = t(description)
                textSize = if (isTvRemote) 14f else 11f
                setTextColor(muted)
                setPadding(0, dp(4), 0, 0)
            })
            item.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            item.addView(TextView(activity).apply {
                text = t(trailing)
                textSize = if (isTvRemote) 24f else 18f
                gravity = Gravity.CENTER
                setTextColor(accent)
            })
            item.contentDescription = t(title) + (if (BrowseXColors.isArabic(activity.currentBrowserSettings)) "، " else ", ") + t(description)
            content.addView(item, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(8)
            })
        }

        section("General", "Choose how you browse")
        row("Search engine", "Currently: ${settings.searchEngine}", "Change") {
            val engines = arrayOf("Google", "DuckDuckGo", "Bing", "Brave", "Ecosia", "Startpage", "Yahoo")
            AlertDialog.Builder(activity)
                .setTitle("Default search engine")
                .setSingleChoiceItems(engines, engines.indexOf(settings.searchEngine).coerceAtLeast(0)) { choice, index ->
                    activity.updateBrowserSettings({ it.copy(searchEngine = engines[index]) }) {
                        choice.dismiss()
                        dialog.dismiss()
                        show()
                    }
                }
                .setNegativeButton(t("Cancel"), null)
                .show()
        }

        row(
            "Address bar position",
            "Choose top or bottom on compact screens",
            t(when (BrowserPreferencePolicy.normalizeAddressBarPosition(settings.addressBarPosition)) {
                "top" -> "Top"
                "bottom" -> "Bottom"
                else -> "Automatic"
            })
        ) {
            val values = arrayOf("auto", "top", "bottom")
            val labels = arrayOf(t("Automatic"), t("Top"), t("Bottom"))
            val selected = values.indexOf(
                BrowserPreferencePolicy.normalizeAddressBarPosition(settings.addressBarPosition)
            ).coerceAtLeast(0)
            AlertDialog.Builder(activity)
                .setTitle(t("Address bar position"))
                .setSingleChoiceItems(labels, selected) { choice, index ->
                    activity.updateBrowserSettings({ it.copy(addressBarPosition = values[index]) }) {
                        choice.dismiss()
                        dialog.dismiss()
                        show()
                    }
                }
                .setNegativeButton(t("Cancel"), null)
                .show()
        }

        row("Theme", "Choose the browser appearance", t(when (settings.themeMode) {
            "dark" -> "Dark"; "light" -> "Light"; else -> "System default"
        })) {
            val values = arrayOf("system", "light", "dark")
            val labels = arrayOf(t("System default"), t("Light"), t("Dark"))
            AlertDialog.Builder(activity)
                .setTitle(t("Theme"))
                .setSingleChoiceItems(labels, values.indexOf(settings.themeMode).coerceAtLeast(0)) { choice, index ->
                    activity.updateBrowserSettings({ it.copy(themeMode = values[index]) }) { updated ->
                        AppCompatDelegate.setDefaultNightMode(when (updated.themeMode) {
                            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                            "light" -> AppCompatDelegate.MODE_NIGHT_NO
                            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        })
                        choice.dismiss()
                        dialog.dismiss()
                        activity.recreate()
                    }
                }
                .setNegativeButton(t("Cancel"), null)
                .show()
        }
        row(
            "Accent color",
            "Choose the highlight color used across browser controls",
            t(when (settings.accentColor) {
                "violet" -> "Violet"
                "emerald" -> "Emerald"
                "rose" -> "Rose"
                "amber" -> "Amber"
                else -> "Ocean"
            })
        ) {
            val values = arrayOf("ocean", "violet", "emerald", "rose", "amber")
            val labels = arrayOf(
                t("Ocean"), t("Violet"), t("Emerald"), t("Rose"), t("Amber")
            )
            AlertDialog.Builder(activity)
                .setTitle(t("Accent color"))
                .setSingleChoiceItems(labels, values.indexOf(settings.accentColor).coerceAtLeast(0)) { choice, index ->
                    activity.updateBrowserSettings({ it.copy(accentColor = values[index]) }) {
                        choice.dismiss()
                        dialog.dismiss()
                        show()
                    }
                }
                .setNegativeButton(t("Cancel"), null)
                .show()
        }

        val availableLanguages = (
            java.util.Locale.getISOLanguages().map { java.util.Locale.forLanguageTag(it) } +
                java.util.Locale.getAvailableLocales().filter {
                    it.language.length in 2..3 && it.language != "und"
                }
            )
            .distinctBy { it.language to it.script }
            .sortedBy { it.getDisplayName(it).lowercase(it) }
        val languageValues = arrayOf("system") + availableLanguages.map { it.toLanguageTag() }
        val languageLabels = arrayOf(t("System language")) + availableLanguages.map { it.getDisplayName(it) }
        val selectedLocale = java.util.Locale.forLanguageTag(settings.languageMode)
        val exactLanguageIndex = languageValues.indexOf(settings.languageMode)
        val matchingLanguageIndex = availableLanguages.indexOfFirst {
            it.language == selectedLocale.language &&
                (selectedLocale.script.isBlank() || it.script == selectedLocale.script)
        }
        val selectedLanguage = when {
            exactLanguageIndex >= 0 -> exactLanguageIndex
            matchingLanguageIndex >= 0 -> matchingLanguageIndex + 1
            else -> 0
        }
        val currentLanguageLabel = if (settings.languageMode == "system") {
            t("System language")
        } else {
            java.util.Locale.forLanguageTag(settings.languageMode).let { it.getDisplayName(it) }
        }
        row("Language", "Choose the app language", currentLanguageLabel) {
            AlertDialog.Builder(activity)
                .setTitle(t("Language"))
                .setSingleChoiceItems(languageLabels, selectedLanguage) { choice, index ->
                    activity.updateBrowserSettings({ it.copy(languageMode = languageValues[index]) }) { updated ->
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags(if (updated.languageMode == "system") "" else updated.languageMode)
                        )
                        choice.dismiss()
                        dialog.dismiss()
                        activity.recreate()
                    }
                }
                .setNegativeButton(t("Cancel"), null)
                .show()
        }
        row("Website notifications", "Allow websites to send Android notifications", t(if (settings.siteNotificationsEnabled) "On" else "Off")) {
            activity.updateBrowserSettings({ it.copy(siteNotificationsEnabled = !settings.siteNotificationsEnabled) }) { updated ->
                setWebsiteNotifications(updated.siteNotificationsEnabled)
                dialog.dismiss()
                show()
            }
        }
        row(
            "Android notification permission",
            "Manage notification permission in Android settings",
            t(if (NotificationManagerCompat.from(activity).areNotificationsEnabled()) "Enabled" else "Blocked")
        ) {
            try {
                activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName))
            } catch (_: Exception) {
                activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${activity.packageName}")))
            }
        }

        row(
            "Android app permissions",
            "Manage camera, microphone and location access",
            t("Manage")
        ) {
            activity.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${activity.packageName}"))
            )
        }

        section("Your library", "Your saved pages stay on this device")
        row("Bookmarks", "Open and manage saved websites", action = {
            dialog.dismiss()
            openBookmarks()
        })
        row("Browsing history", "Review or remove visited pages", action = {
            dialog.dismiss()
            openHistory()
        })
        row("Downloads", "Open downloaded files", action = {
            dialog.dismiss()
            openDownloads()
        })
        row("Import bookmarks", "Back up or restore your saved websites", action = {
            dialog.dismiss()
            importBookmarks()
        })
        row("Export bookmarks", "Back up or restore your saved websites", action = {
            dialog.dismiss()
            exportBookmarks()
        })

        section("Accessibility & appearance")
        row(
            "Reduce transparency",
            "Use opaque browser surfaces for clearer contrast and less visual layering",
            t(if (settings.reduceTransparency) "On" else "Off")
        ) {
            val nextValue = !settings.reduceTransparency
            activity.updateBrowserSettings({ it.copy(reduceTransparency = nextValue) }) {
                dialog.dismiss()
                show()
            }
        }

        row(
            "Increase contrast",
            "Strengthen borders and foreground contrast across browser controls",
            t(if (settings.highContrast) "On" else "Off")
        ) {
            val nextValue = !settings.highContrast
            activity.updateBrowserSettings({ it.copy(highContrast = nextValue) }) {
                dialog.dismiss()
                show()
            }
        }
        row(
            "Reduce motion",
            "Limit non-essential interface motion and visual transitions",
            t(if (settings.reduceMotion) "On" else "Off")
        ) {
            val nextValue = !settings.reduceMotion
            activity.updateBrowserSettings({ it.copy(reduceMotion = nextValue) }) {
                dialog.dismiss()
                show()
            }
        }

        section("Home page")
        row("Home page", "Customize shortcuts and sections", action = {
            dialog.dismiss()
            showHomePreferences()
        })

        section("Privacy & data")
        row("Privacy center", "Review and clear cookies, site data and permissions", action = {
            dialog.dismiss()
            openPrivacyCenter()
        })

        row("Site permissions", "Review and change saved camera, microphone, location and notification choices", action = {
            dialog.dismiss()
            openSitePermissions()
        })

        row(
            "uBlock Origin",
            when {
                !settings.adBlockInstalled -> "Install and manage real ad and tracker blocking"
                settings.adBlockEnabled -> "Installed and enabled"
                else -> "Installed but disabled"
            },
            t("Manage")
        ) {
            dialog.dismiss()
            manageUBlockOrigin()
        }

        row(
            "Password autofill",
            "Use BrowseX to fill saved passwords in supported Android apps",
            t("Manage")
        ) {
            dialog.dismiss()
            openAutofillSettings()
        }
        row("Clear browsing history", "Remove the browsing history stored by Browse X", "Clear") {
            AlertDialog.Builder(activity)
                 .setTitle(t("Clear browsing history?"))
                .setMessage(t("This removes the history list saved by Browse X. It does not delete downloaded files or bookmarks."))
                .setNegativeButton("Cancel", null)
                .setPositiveButton(t("Clear")) { _, _ ->
                    clearHistory()
                    dialog.dismiss()
                    show()
                }
                .show()
        }

        section("About")
        row("About Browse X", "A calmer way to explore the web", "ⓘ") {
            AlertDialog.Builder(activity)
                .setTitle("Browse X")
                .setMessage(t("Browse X is powered by Mozilla GeckoView. Some browser features are still being built."))
                .setPositiveButton(t("Done"), null)
                .show()
        }

        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = TextView(activity).apply {
            text = t("Browse X  ·  Built for the open web")
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(0, dp(10), 0, dp(2))
        }
        page.addView(footer)

        dialog = AlertDialog.Builder(activity).setView(page).create()
        close.setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener {
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}

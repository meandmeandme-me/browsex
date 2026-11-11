package com.browsex.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.browsex.app.storage.BrowserSettings
import java.util.Locale

internal enum class BrowseXSettingsAction {
    BOOKMARKS, HISTORY, DOWNLOADS, IMPORT_BOOKMARKS, EXPORT_BOOKMARKS,
    PRIVACY_CENTER, SITE_PERMISSIONS, UBLOCK, AUTOFILL,
    CLEAR_HISTORY, ANDROID_NOTIFICATION_SETTINGS, ANDROID_APP_PERMISSIONS, ABOUT
}

private enum class BrowseXSettingChoice {
    SEARCH_ENGINE, ADDRESS_BAR_POSITION, THEME, ACCENT, LANGUAGE, HOME_LAYOUT
}

@Composable
internal fun BrowseXSettingsSheet(
    settings: BrowserSettings,
    androidNotificationsEnabled: Boolean,
    onDismiss: () -> Unit,
    onUpdateSettings: ((BrowserSettings) -> BrowserSettings) -> Unit,
    onAction: (BrowseXSettingsAction) -> Unit
) {
    val context = LocalContext.current
    fun t(en: String, ar: String = en): String = BrowseXStrings.text(context, en, ar)
    var choice by remember { mutableStateOf<BrowseXSettingChoice?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(27.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 17.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = t("Settings", "الإعدادات"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = t("Make BrowseX work the way you like", "خصص BrowseX بالطريقة التي تناسبك"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(t("Done", "تم"))
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    item { SettingsSection(t("General", "عام")) }
                    item {
                        SettingsRow(
                            t("Search engine", "محرك البحث"),
                            t("Choose the service used for web searches", "اختر محرك البحث الافتراضي"),
                            settings.searchEngine,
                            onClick = { choice = BrowseXSettingChoice.SEARCH_ENGINE }
                        )
                    }
                    item {
                        SettingsRow(
                            t("Address bar position", "موضع شريط العنوان"),
                            t("Choose where browser controls appear", "حدد موضع عناصر التحكم"),
                            t(when (settings.addressBarPosition) {
                                "top" -> "Top"
                                "bottom" -> "Bottom"
                                else -> "Automatic"
                            }, when (settings.addressBarPosition) {
                                "top" -> "أعلى"
                                "bottom" -> "أسفل"
                                else -> "تلقائي"
                            }),
                            onClick = { choice = BrowseXSettingChoice.ADDRESS_BAR_POSITION }
                        )
                    }
                    item {
                        SettingsRow(
                            t("Theme", "المظهر"),
                            t("Choose light, dark, or system appearance", "اختر المظهر الفاتح أو الداكن أو حسب النظام"),
                            t(when (settings.themeMode) {
                                "dark" -> "Dark"
                                "light" -> "Light"
                                else -> "System"
                            }, when (settings.themeMode) {
                                "dark" -> "داكن"
                                "light" -> "فاتح"
                                else -> "حسب النظام"
                            }),
                            onClick = { choice = BrowseXSettingChoice.THEME }
                        )
                    }
                    item {
                        SettingsRow(
                            t("Accent color", "اللون الرئيسي"),
                            t("Personalize the browser highlight color", "خصص لون عناصر المتصفح"),
                            t(settings.accentColor.replaceFirstChar { it.uppercase() }),
                            onClick = { choice = BrowseXSettingChoice.ACCENT }
                        )
                    }
                    item {
                        val language = when (settings.languageMode) {
                            "ar" -> "العربية"
                            "en" -> "English"
                            else -> t("System language", "لغة النظام")
                        }
                        SettingsRow(
                            t("Language", "اللغة"),
                            t("Change the language of browser controls", "غيّر لغة واجهة المتصفح"),
                            language,
                            onClick = { choice = BrowseXSettingChoice.LANGUAGE }
                        )
                    }
                    item {
                        SettingsToggleRow(
                            t("Website notifications", "إشعارات المواقع"),
                            t("Allow websites to send Android notifications", "السماح للمواقع بإرسال إشعارات أندرويد"),
                            settings.siteNotificationsEnabled,
                            onCheckedChange = { enabled ->
                                onUpdateSettings { it.copy(siteNotificationsEnabled = enabled) }
                            }
                        )
                    }
                    item {
                        SettingsRow(
                            t("Android notification permission", "إذن إشعارات أندرويد"),
                            t("Open Android notification settings", "افتح إعدادات إشعارات أندرويد"),
                            if (androidNotificationsEnabled) t("Enabled", "مفعّل") else t("Blocked", "محظور"),
                            onClick = { onAction(BrowseXSettingsAction.ANDROID_NOTIFICATION_SETTINGS) }
                        )
                    }
                    item {
                        SettingsRow(
                            t("Android app permissions", "أذونات التطبيق"),
                            t("Manage camera, microphone, and location access", "إدارة أذونات الكاميرا والميكروفون والموقع"),
                            t("Manage", "إدارة"),
                            onClick = { onAction(BrowseXSettingsAction.ANDROID_APP_PERMISSIONS) }
                        )
                    }

                    item { SettingsSection(t("Your library", "مكتبتك")) }
                    item { SettingsRow(t("Bookmarks", "المفضلة"), t("Open and organize saved websites", "افتح المواقع المحفوظة ونظمها"), onClick = { onAction(BrowseXSettingsAction.BOOKMARKS) }) }
                    item { SettingsRow(t("Browsing history", "سجل التصفح"), t("Review or remove visited pages", "راجع الصفحات التي زرتها أو احذفها"), onClick = { onAction(BrowseXSettingsAction.HISTORY) }) }
                    item { SettingsRow(t("Downloads", "التنزيلات"), t("View and manage downloaded files", "عرض الملفات التي تم تنزيلها وإدارتها"), onClick = { onAction(BrowseXSettingsAction.DOWNLOADS) }) }
                    item { SettingsRow(t("Import bookmarks", "استيراد المفضلة"), t("Bring bookmarks from another browser", "استيراد المفضلة من متصفح آخر"), onClick = { onAction(BrowseXSettingsAction.IMPORT_BOOKMARKS) }) }
                    item { SettingsRow(t("Export bookmarks", "تصدير المفضلة"), t("Create a portable bookmark backup", "إنشاء نسخة احتياطية من المفضلة"), onClick = { onAction(BrowseXSettingsAction.EXPORT_BOOKMARKS) }) }

                    item { SettingsSection(t("Accessibility & appearance", "إمكانية الوصول والمظهر")) }
                    item {
                        SettingsToggleRow(
                            t("Reduce transparency", "تقليل الشفافية"),
                            t("Use opaque surfaces for clearer contrast", "استخدم خلفيات معتمة لزيادة الوضوح"),
                            settings.reduceTransparency,
                            onCheckedChange = { enabled -> onUpdateSettings { it.copy(reduceTransparency = enabled) } }
                        )
                    }
                    item {
                        SettingsToggleRow(
                            t("Increase contrast", "زيادة التباين"),
                            t("Strengthen borders and text contrast", "تقوية تباين النصوص والحدود"),
                            settings.highContrast,
                            onCheckedChange = { enabled -> onUpdateSettings { it.copy(highContrast = enabled) } }
                        )
                    }
                    item {
                        SettingsToggleRow(
                            t("Reduce motion", "تقليل الحركة"),
                            t("Limit non-essential animations", "تقليل الحركات غير الضرورية"),
                            settings.reduceMotion,
                            onCheckedChange = { enabled -> onUpdateSettings { it.copy(reduceMotion = enabled) } }
                        )
                    }

                    item { SettingsSection(t("Home page", "الصفحة الرئيسية")) }
                    item {
                        SettingsToggleRow(
                            t("Show shortcuts", "إظهار الاختصارات"),
                            t("Show your saved websites on the home page", "إظهار المواقع المحفوظة في الصفحة الرئيسية"),
                            settings.homeShowQuickLinks,
                            onCheckedChange = { enabled -> onUpdateSettings { it.copy(homeShowQuickLinks = enabled) } }
                        )
                    }
                    item {
                        SettingsToggleRow(
                            t("Show recent pages", "إظهار الصفحات الأخيرة"),
                            t("Continue browsing from your recent history", "متابعة التصفح من سجلّك الحديث"),
                            settings.homeShowDiscovery,
                            onCheckedChange = { enabled -> onUpdateSettings { it.copy(homeShowDiscovery = enabled) } }
                        )
                    }
                    item {
                        SettingsRow(
                            t("Shortcut layout", "تخطيط الاختصارات"),
                            t("Choose a grid or compact list", "اختر شبكة أو قائمة مختصرة"),
                            t(if (settings.homeLayout == "list") "List" else "Grid", if (settings.homeLayout == "list") "قائمة" else "شبكة"),
                            onClick = { choice = BrowseXSettingChoice.HOME_LAYOUT }
                        )
                    }

                    item { SettingsSection(t("Privacy & data", "الخصوصية والبيانات")) }
                    item { SettingsRow(t("Privacy center", "مركز الخصوصية"), t("Review and clear cookies and site data", "مراجعة ملفات الارتباط وبيانات المواقع ومسحها"), onClick = { onAction(BrowseXSettingsAction.PRIVACY_CENTER) }) }
                    item { SettingsRow(t("Site permissions", "أذونات المواقع"), t("Review saved camera, microphone, location, and notification choices", "مراجعة أذونات المواقع المحفوظة"), onClick = { onAction(BrowseXSettingsAction.SITE_PERMISSIONS) }) }
                    item {
                        val protection = when {
                            !settings.adBlockInstalled -> t("Not installed", "غير مثبت")
                            settings.adBlockEnabled -> t("Installed and enabled", "مثبت ومفعّل")
                            else -> t("Installed but disabled", "مثبت لكنه متوقف")
                        }
                        SettingsRow(
                            "uBlock Origin",
                            t("Manage ad and tracker protection", "إدارة منع الإعلانات والمتتبعات"),
                            protection,
                            onClick = { onAction(BrowseXSettingsAction.UBLOCK) }
                        )
                    }
                    item { SettingsRow(t("Password autofill", "التعبئة التلقائية لكلمات المرور"), t("Use BrowseX to fill passwords in supported Android apps", "استخدم BrowseX لملء كلمات المرور في التطبيقات المدعومة"), t("Manage", "إدارة"), onClick = { onAction(BrowseXSettingsAction.AUTOFILL) }) }
                    item { SettingsRow(t("Clear browsing history", "مسح سجل التصفح"), t("Remove the history list stored on this device", "حذف سجل التصفح المحفوظ على هذا الجهاز"), t("Clear", "مسح"), onClick = { onAction(BrowseXSettingsAction.CLEAR_HISTORY) }) }

                    item { SettingsSection(t("About", "حول")) }
                    item { SettingsRow(t("About BrowseX", "حول BrowseX"), t("Browser engine, version, and open-source notices", "محرك المتصفح والإصدار وإشعارات المصادر المفتوحة"), "ⓘ", onClick = { onAction(BrowseXSettingsAction.ABOUT) }) }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }

    val choiceOptions: List<Pair<String, String>> = when (choice) {
        BrowseXSettingChoice.SEARCH_ENGINE -> listOf("Google" to "Google", "DuckDuckGo" to "DuckDuckGo", "Bing" to "Bing", "Brave" to "Brave", "Ecosia" to "Ecosia", "Startpage" to "Startpage", "Yahoo" to "Yahoo")
        BrowseXSettingChoice.ADDRESS_BAR_POSITION -> listOf("auto" to t("Automatic", "تلقائي"), "top" to t("Top", "أعلى"), "bottom" to t("Bottom", "أسفل"))
        BrowseXSettingChoice.THEME -> listOf("system" to t("System default", "حسب النظام"), "light" to t("Light", "فاتح"), "dark" to t("Dark", "داكن"))
        BrowseXSettingChoice.ACCENT -> listOf("ocean" to t("Ocean", "محيطي"), "violet" to t("Violet", "بنفسجي"), "emerald" to t("Emerald", "زمردي"), "rose" to t("Rose", "وردي"), "amber" to t("Amber", "كهرماني"))
        BrowseXSettingChoice.LANGUAGE -> listOf("system" to t("System language", "لغة النظام"), "en" to "English", "ar" to "العربية")
        BrowseXSettingChoice.HOME_LAYOUT -> listOf("grid" to t("Grid", "شبكة"), "list" to t("List", "قائمة"))
        null -> emptyList()
    }
    if (choice != null) {
        val current = when (choice) {
            BrowseXSettingChoice.SEARCH_ENGINE -> settings.searchEngine
            BrowseXSettingChoice.ADDRESS_BAR_POSITION -> settings.addressBarPosition
            BrowseXSettingChoice.THEME -> settings.themeMode
            BrowseXSettingChoice.ACCENT -> settings.accentColor
            BrowseXSettingChoice.LANGUAGE -> settings.languageMode
            BrowseXSettingChoice.HOME_LAYOUT -> settings.homeLayout
            null -> ""
        }
        AlertDialog(
            onDismissRequest = { choice = null },
            title = { Text(t(when (choice) {
                BrowseXSettingChoice.SEARCH_ENGINE -> "Search engine"
                BrowseXSettingChoice.ADDRESS_BAR_POSITION -> "Address bar position"
                BrowseXSettingChoice.THEME -> "Theme"
                BrowseXSettingChoice.ACCENT -> "Accent color"
                BrowseXSettingChoice.LANGUAGE -> "Language"
                BrowseXSettingChoice.HOME_LAYOUT -> "Shortcut layout"
                null -> "Settings"
            })) },
            text = {
                Column {
                    choiceOptions.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    when (choice) {
                                        BrowseXSettingChoice.SEARCH_ENGINE -> onUpdateSettings { it.copy(searchEngine = value) }
                                        BrowseXSettingChoice.ADDRESS_BAR_POSITION -> onUpdateSettings { it.copy(addressBarPosition = value) }
                                        BrowseXSettingChoice.THEME -> onUpdateSettings { it.copy(themeMode = value) }
                                        BrowseXSettingChoice.ACCENT -> onUpdateSettings { it.copy(accentColor = value) }
                                        BrowseXSettingChoice.LANGUAGE -> onUpdateSettings { it.copy(languageMode = value) }
                                        BrowseXSettingChoice.HOME_LAYOUT -> onUpdateSettings { it.copy(homeLayout = value) }
                                        null -> Unit
                                    }
                                    choice = null
                                }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = current == value, onClick = {
                                when (choice) {
                                    BrowseXSettingChoice.SEARCH_ENGINE -> onUpdateSettings { it.copy(searchEngine = value) }
                                    BrowseXSettingChoice.ADDRESS_BAR_POSITION -> onUpdateSettings { it.copy(addressBarPosition = value) }
                                    BrowseXSettingChoice.THEME -> onUpdateSettings { it.copy(themeMode = value) }
                                    BrowseXSettingChoice.ACCENT -> onUpdateSettings { it.copy(accentColor = value) }
                                    BrowseXSettingChoice.LANGUAGE -> onUpdateSettings { it.copy(languageMode = value) }
                                    BrowseXSettingChoice.HOME_LAYOUT -> onUpdateSettings { it.copy(homeLayout = value) }
                                    null -> Unit
                                }
                                choice = null
                            })
                            Text(label, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choice = null }) { Text(t("Close", "إغلاق")) }
            }
        )
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 7.dp, start = 3.dp)
    )
}

@Composable
private fun SettingsRow(
    title: String,
    description: String,
    trailing: String = "",
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (trailing.isNotBlank()) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

package com.wren.app

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
import com.wren.app.storage.BrowserSettings
import java.util.Locale

internal enum class WrenSettingsAction {
    BOOKMARKS, HISTORY, DOWNLOADS, IMPORT_BOOKMARKS, EXPORT_BOOKMARKS,
    HOME_PAGE, PRIVACY_CENTER, SITE_PERMISSIONS, UBLOCK, AUTOFILL,
    CLEAR_HISTORY, ANDROID_NOTIFICATION_SETTINGS, ANDROID_APP_PERMISSIONS, ABOUT
}

private enum class WrenSettingChoice {
    SEARCH_ENGINE, ADDRESS_BAR_POSITION, THEME, ACCENT, LANGUAGE
}

@Composable
internal fun WrenSettingsSheet(
    settings: BrowserSettings,
    androidNotificationsEnabled: Boolean,
    onDismiss: () -> Unit,
    onUpdateSettings: ((BrowserSettings) -> BrowserSettings) -> Unit,
    onAction: (WrenSettingsAction) -> Unit
) {
    val context = LocalContext.current
    fun t(en: String, ar: String = en): String = WrenStrings.text(context, en, ar)
    var choice by remember { mutableStateOf<WrenSettingChoice?>(null) }

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
                            text = t("Make Wren work the way you like", "خصص Wren بالطريقة التي تناسبك"),
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
                            onClick = { choice = WrenSettingChoice.SEARCH_ENGINE }
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
                            onClick = { choice = WrenSettingChoice.ADDRESS_BAR_POSITION }
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
                            onClick = { choice = WrenSettingChoice.THEME }
                        )
                    }
                    item {
                        SettingsRow(
                            t("Accent color", "اللون الرئيسي"),
                            t("Personalize the browser highlight color", "خصص لون عناصر المتصفح"),
                            t(settings.accentColor.replaceFirstChar { it.uppercase() }),
                            onClick = { choice = WrenSettingChoice.ACCENT }
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
                            onClick = { choice = WrenSettingChoice.LANGUAGE }
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
                            onClick = { onAction(WrenSettingsAction.ANDROID_NOTIFICATION_SETTINGS) }
                        )
                    }
                    item {
                        SettingsRow(
                            t("Android app permissions", "أذونات التطبيق"),
                            t("Manage camera, microphone, and location access", "إدارة أذونات الكاميرا والميكروفون والموقع"),
                            t("Manage", "إدارة"),
                            onClick = { onAction(WrenSettingsAction.ANDROID_APP_PERMISSIONS) }
                        )
                    }

                    item { SettingsSection(t("Your library", "مكتبتك")) }
                    item { SettingsRow(t("Bookmarks", "المفضلة"), t("Open and organize saved websites", "افتح المواقع المحفوظة ونظمها"), onClick = { onAction(WrenSettingsAction.BOOKMARKS) }) }
                    item { SettingsRow(t("Browsing history", "سجل التصفح"), t("Review or remove visited pages", "راجع الصفحات التي زرتها أو احذفها"), onClick = { onAction(WrenSettingsAction.HISTORY) }) }
                    item { SettingsRow(t("Downloads", "التنزيلات"), t("View and manage downloaded files", "عرض الملفات التي تم تنزيلها وإدارتها"), onClick = { onAction(WrenSettingsAction.DOWNLOADS) }) }
                    item { SettingsRow(t("Import bookmarks", "استيراد المفضلة"), t("Bring bookmarks from another browser", "استيراد المفضلة من متصفح آخر"), onClick = { onAction(WrenSettingsAction.IMPORT_BOOKMARKS) }) }
                    item { SettingsRow(t("Export bookmarks", "تصدير المفضلة"), t("Create a portable bookmark backup", "إنشاء نسخة احتياطية من المفضلة"), onClick = { onAction(WrenSettingsAction.EXPORT_BOOKMARKS) }) }

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
                    item { SettingsRow(t("Customize home page", "تخصيص الصفحة الرئيسية"), t("Choose shortcuts and home sections", "اختر الاختصارات وأقسام الصفحة الرئيسية"), onClick = { onAction(WrenSettingsAction.HOME_PAGE) }) }

                    item { SettingsSection(t("Privacy & data", "الخصوصية والبيانات")) }
                    item { SettingsRow(t("Privacy center", "مركز الخصوصية"), t("Review and clear cookies and site data", "مراجعة ملفات الارتباط وبيانات المواقع ومسحها"), onClick = { onAction(WrenSettingsAction.PRIVACY_CENTER) }) }
                    item { SettingsRow(t("Site permissions", "أذونات المواقع"), t("Review saved camera, microphone, location, and notification choices", "مراجعة أذونات المواقع المحفوظة"), onClick = { onAction(WrenSettingsAction.SITE_PERMISSIONS) }) }
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
                            onClick = { onAction(WrenSettingsAction.UBLOCK) }
                        )
                    }
                    item { SettingsRow(t("Password autofill", "التعبئة التلقائية لكلمات المرور"), t("Use Wren to fill passwords in supported Android apps", "استخدم Wren لملء كلمات المرور في التطبيقات المدعومة"), t("Manage", "إدارة"), onClick = { onAction(WrenSettingsAction.AUTOFILL) }) }
                    item { SettingsRow(t("Clear browsing history", "مسح سجل التصفح"), t("Remove the history list stored on this device", "حذف سجل التصفح المحفوظ على هذا الجهاز"), t("Clear", "مسح"), onClick = { onAction(WrenSettingsAction.CLEAR_HISTORY) }) }

                    item { SettingsSection(t("About", "حول")) }
                    item { SettingsRow(t("About Wren", "حول Wren"), t("Browser engine, version, and open-source notices", "محرك المتصفح والإصدار وإشعارات المصادر المفتوحة"), "ⓘ", onClick = { onAction(WrenSettingsAction.ABOUT) }) }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }

    val choiceOptions: List<Pair<String, String>> = when (choice) {
        WrenSettingChoice.SEARCH_ENGINE -> listOf("Google" to "Google", "DuckDuckGo" to "DuckDuckGo", "Bing" to "Bing", "Brave" to "Brave", "Ecosia" to "Ecosia", "Startpage" to "Startpage", "Yahoo" to "Yahoo")
        WrenSettingChoice.ADDRESS_BAR_POSITION -> listOf("bottom" to t("Bottom", "أسفل"), "top" to t("Top", "أعلى"))
        WrenSettingChoice.THEME -> listOf("system" to t("System default", "حسب النظام"), "light" to t("Light", "فاتح"), "dark" to t("Dark", "داكن"))
        WrenSettingChoice.ACCENT -> listOf("ocean" to t("Ocean", "محيطي"), "violet" to t("Violet", "بنفسجي"), "emerald" to t("Emerald", "زمردي"), "rose" to t("Rose", "وردي"), "amber" to t("Amber", "كهرماني"))
        WrenSettingChoice.LANGUAGE -> listOf("system" to t("System language", "لغة النظام"), "en" to "English", "ar" to "العربية")
        null -> emptyList()
    }
    if (choice != null) {
        val current = when (choice) {
            WrenSettingChoice.SEARCH_ENGINE -> settings.searchEngine
            WrenSettingChoice.ADDRESS_BAR_POSITION -> settings.addressBarPosition
            WrenSettingChoice.THEME -> settings.themeMode
            WrenSettingChoice.ACCENT -> settings.accentColor
            WrenSettingChoice.LANGUAGE -> settings.languageMode
            null -> ""
        }
        AlertDialog(
            onDismissRequest = { choice = null },
            title = { Text(t(when (choice) {
                WrenSettingChoice.SEARCH_ENGINE -> "Search engine"
                WrenSettingChoice.ADDRESS_BAR_POSITION -> "Address bar position"
                WrenSettingChoice.THEME -> "Theme"
                WrenSettingChoice.ACCENT -> "Accent color"
                WrenSettingChoice.LANGUAGE -> "Language"
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
                                        WrenSettingChoice.SEARCH_ENGINE -> onUpdateSettings { it.copy(searchEngine = value) }
                                        WrenSettingChoice.ADDRESS_BAR_POSITION -> onUpdateSettings { it.copy(addressBarPosition = value) }
                                        WrenSettingChoice.THEME -> onUpdateSettings { it.copy(themeMode = value) }
                                        WrenSettingChoice.ACCENT -> onUpdateSettings { it.copy(accentColor = value) }
                                        WrenSettingChoice.LANGUAGE -> onUpdateSettings { it.copy(languageMode = value) }
                                        null -> Unit
                                    }
                                    choice = null
                                }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = current == value, onClick = {
                                when (choice) {
                                    WrenSettingChoice.SEARCH_ENGINE -> onUpdateSettings { it.copy(searchEngine = value) }
                                    WrenSettingChoice.ADDRESS_BAR_POSITION -> onUpdateSettings { it.copy(addressBarPosition = value) }
                                    WrenSettingChoice.THEME -> onUpdateSettings { it.copy(themeMode = value) }
                                    WrenSettingChoice.ACCENT -> onUpdateSettings { it.copy(accentColor = value) }
                                    WrenSettingChoice.LANGUAGE -> onUpdateSettings { it.copy(languageMode = value) }
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

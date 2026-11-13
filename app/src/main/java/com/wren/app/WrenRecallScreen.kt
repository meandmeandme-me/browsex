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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

internal data class WrenRecallState(
    val openTabs: List<Pair<String, String>>,
    val bookmarks: List<Pair<String, String>>,
    val history: List<Pair<String, String>>
)

@Composable
internal fun WrenRecallScreen(
    state: WrenRecallState,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit
) {
    val context = LocalContext.current
    fun t(en: String, ar: String = en) = WrenStrings.text(context, en, ar)
    var query by rememberSaveable { mutableStateOf("") }
    val normalized = remember(query) { BrowserTextNormalizer.normalize(query.trim()) }
    val sections = listOf(
        t("Open tabs", "التبويبات المفتوحة") to state.openTabs.take(500),
        t("Bookmarks", "المفضلة") to state.bookmarks.take(500),
        t("Recent history", "السجل الحديث") to state.history.take(500)
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(17.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            t("Find anything", "اعثر على أي شيء"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            t("Search open tabs, bookmarks, and recent history", "ابحث في التبويبات والمفضلة والسجل الحديث"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) { Text(t("Done", "تم")) }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.filterNot(Char::isISOControl).take(256) },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text(t("Search pages or websites", "ابحث عن صفحة أو موقع")) }
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    sections.forEach { (label, rows) ->
                        val filtered = rows.asSequence()
                            .filter { (title, url) ->
                                normalized.isBlank() || listOf(title, url, runCatching {
                                    android.net.Uri.parse(url).host.orEmpty()
                                }.getOrDefault("")).any {
                                    BrowserTextNormalizer.normalize(it).contains(normalized)
                                }
                            }
                            .take(12)
                            .toList()
                        if (filtered.isNotEmpty()) {
                            item(key = "section:$label") {
                                Text(
                                    label.uppercase(),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 10.dp, start = 3.dp, bottom = 3.dp)
                                )
                            }
                            items(filtered) { (title, url) ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpen(url) },
                                    shape = RoundedCornerShape(15.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f))
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
                                        Text(
                                            title.ifBlank { runCatching { android.net.Uri.parse(url).host }.getOrNull().orEmpty().ifBlank { url } },
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            url.filterNot(Char::isISOControl).take(2048),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (sections.all { (_, rows) ->
                            rows.none { (title, url) ->
                                normalized.isBlank() || listOf(title, url).any {
                                    BrowserTextNormalizer.normalize(it).contains(normalized)
                                }
                            }
                        }) {
                        item {
                            Text(
                                t("No matching pages. Try another search.", "لا توجد صفحات مطابقة. جرّب بحثاً آخر."),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 28.dp)
                            )
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

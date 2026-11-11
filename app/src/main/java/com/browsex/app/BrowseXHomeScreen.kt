package com.browsex.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

internal data class BrowseXHomeLink(
    val title: String,
    val url: String
)

internal data class BrowseXHomeState(
    val shortcuts: List<BrowseXHomeLink> = emptyList(),
    val recentPages: List<BrowseXHomeLink> = emptyList(),
    val showQuickLinks: Boolean = true,
    val showDiscovery: Boolean = true,
    val layout: String = "grid"
)

@Composable
internal fun BrowseXHomeScreen(
    state: BrowseXHomeState,
    isDark: Boolean,
    onSearch: (String) -> Unit,
    onOpen: (String) -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
    onPrivateTab: () -> Unit,
    onCustomize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    fun t(en: String, ar: String = en) = BrowseXStrings.text(context, en, ar)
    var query by rememberSaveable { mutableStateOf("") }
    val background = if (isDark) {
        Brush.linearGradient(listOf(Color(0xFF101827), Color(0xFF17243A), Color(0xFF182035)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFE8F4FA), Color(0xFFF4F5FC), Color(0xFFE8ECFA)))
    }
    val shortcuts = state.shortcuts.take(12)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    t("YOUR SPACE, YOUR WEB", "مساحتك الخاصة على الويب"),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    t("What would you like to explore?", "ماذا تريد أن تكتشف اليوم؟"),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text(
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(2048) },
            modifier = Modifier.fillMaxWidth().padding(top = 17.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text(t("Search the web or enter an address", "ابحث في الويب أو أدخل عنواناً")) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                val value = query.trim()
                if (value.isNotEmpty()) {
                    focusManager.clearFocus()
                    onSearch(value)
                }
            })
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            HomeAction(t("Bookmarks", "المفضلة"), Icons.Default.Bookmarks, onBookmarks, Modifier.weight(1f))
            HomeAction(t("History", "السجل"), Icons.Default.History, onHistory, Modifier.weight(1f))
            HomeAction(t("Downloads", "التنزيلات"), Icons.Default.Download, onDownloads, Modifier.weight(1f))
            HomeAction(t("Private", "خاص"), Icons.Default.Shield, onPrivateTab, Modifier.weight(1f))
        }

        if (state.showQuickLinks) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 19.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    t("Shortcuts", "اختصارات"),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onCustomize) { Text(t("Manage", "إدارة")) }
            }
            if (state.layout.equals("list", true)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    shortcuts.forEach { link ->
                        HomeLinkRow(link = link, onClick = { onOpen(link.url) })
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 78.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 225.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(shortcuts) { link ->
                        HomeShortcutCard(link = link, onClick = { onOpen(link.url) })
                    }
                }
            }
        }

        if (state.showDiscovery) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    t("Pick up where you left off", "تابع من حيث توقفت"),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onHistory) { Text(t("View all", "عرض الكل")) }
            }
            if (state.recentPages.isEmpty()) {
                Text(
                    t("Your recent pages will appear here.", "ستظهر صفحاتك الأخيرة هنا."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.recentPages.take(5)) { link ->
                        HomeLinkRow(link = link, onClick = { onOpen(link.url) })
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = onSettings,
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(t("Browser settings", "إعدادات المتصفح"), modifier = Modifier.padding(start = 5.dp))
        }
    }
}

@Composable
private fun HomeAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(onClick = onClick, modifier = modifier.height(42.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 5.dp, vertical = 4.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(label, modifier = Modifier.padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HomeShortcutCard(link: BrowseXHomeLink, onClick: () -> Unit) {
    val host = runCatching { android.net.Uri.parse(link.url).host?.removePrefix("www.") }.getOrNull().orEmpty()
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = (host.firstOrNull() ?: link.title.firstOrNull() ?: 'B').uppercase(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                link.title.ifBlank { host },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun HomeLinkRow(link: BrowseXHomeLink, onClick: () -> Unit) {
    val host = runCatching { android.net.Uri.parse(link.url).host?.removePrefix("www.") }.getOrNull().orEmpty()
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(link.title.ifBlank { host }, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(host.ifBlank { link.url }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

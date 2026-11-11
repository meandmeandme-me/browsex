package com.browsex.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import org.mozilla.geckoview.GeckoSession
import java.util.Locale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

internal enum class BrowseXMenuAction(val label: String) {
    BOOKMARK_TOGGLE("Save / remove bookmark"),
    BOOKMARKS("Bookmarks"),
    HISTORY("History"),
    DOWNLOADS("Downloads"),
    SHARE("Share page"),
    COPY_ADDRESS("Copy page address"),
    FIND_IN_PAGE("Find in page"),
    READER("Reader mode"),
    SITE_INFO("Site information"),
    SEARCH_ENGINE("Search engine"),
    CLEAR_HISTORY("Clear browsing history"),
    PRIVACY_CENTER("Privacy center"),
    SETTINGS("Settings"),
    PRIVATE_TAB("New private tab"),
    CLOSE_PRIVATE_TABS("Close private tabs"),
    RECALL("Find anything"),
    PASSWORDS("Password manager"),
    SAVED_SESSIONS("Saved sessions"),
    SAVE_SESSION("Save current session"),
    RECENTLY_CLOSED("Recently closed tabs"),
    CLOSE_ALL_TABS("Close all tabs")
}

internal data class BrowseXChromeState(
    val url: String = "",
    val title: String = "BrowseX",
    val isHome: Boolean = false,
    val homeState: BrowseXHomeState = BrowseXHomeState(),
    val progress: Int = 0,
    val tabCount: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isPrivate: Boolean = false,
    val isDark: Boolean = false,
    val uBlockAvailable: Boolean = false,
    val showNavigationActions: Boolean = true,
    val dockAtTop: Boolean = false,
    val reduceTransparency: Boolean = false,
    val highContrast: Boolean = false,
    val canvasColor: Int = 0xFFF4F7FC.toInt(),
    val panelColor: Int = 0xDDFDFFFF.toInt(),
    val fieldColor: Int = 0xEFFFFFFF.toInt(),
    val inkColor: Int = 0xFF151B2D.toInt(),
    val mutedColor: Int = 0xFF667085.toInt(),
    val accentColor: Int = 0xFF4263EB.toInt(),
    val lineColor: Int = 0x66A8B8D0.toInt()
)

@Composable
internal fun BrowseXBrowserChrome(
    state: BrowseXChromeState,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onHome: () -> Unit,
    onSiteInfo: () -> Unit,
    onBookmark: () -> Unit,
    onNewTab: () -> Unit,
    onOpenTabs: () -> Unit,
    onMenu: () -> Unit,
    onProtection: () -> Unit,
    loadSuggestions: suspend (String) -> List<AddressSuggestion>,
    onSelectSuggestion: (AddressSuggestion) -> Unit,
    menuOpen: Boolean,
    onDismissMenu: () -> Unit,
    onMenuAction: (BrowseXMenuAction) -> Unit,
    tabs: List<BrowserTab>,
    activeTab: BrowserTab?,
    tabsOpen: Boolean,
    onDismissTabs: () -> Unit,
    onSelectTab: (BrowserTab) -> Unit,
    onCloseTab: (BrowserTab) -> Unit,
    onCreateTab: () -> Unit,
    settingsOpen: Boolean,
    settings: com.browsex.app.storage.BrowserSettings,
    androidNotificationsEnabled: Boolean,
    onDismissSettings: () -> Unit,
    onUpdateSettings: ((com.browsex.app.storage.BrowserSettings) -> com.browsex.app.storage.BrowserSettings) -> Unit,
    onSettingsAction: (BrowseXSettingsAction) -> Unit,
    savedPagesOpen: Boolean,
    savedPagesState: BrowseXSavedPagesState,
    onDismissSavedPages: () -> Unit,
    onOpenSavedPage: (String) -> Unit,
    onRemoveSavedPage: (String) -> Unit,
    onRenameBookmark: (String, String) -> Unit,
    onMoveBookmark: (String, String) -> Unit,
    onMoveBookmarkWithinFolder: (String, String, Int) -> Unit,
    onCreateBookmarkFolder: (String) -> Unit,
    onRenameBookmarkFolder: (String, String) -> Unit,
    onDeleteBookmarkFolder: (String) -> Unit,
    onClearSavedHistory: () -> Unit,
    onShareSavedPage: (String) -> Unit,
    onCopySavedPage: (String) -> Unit,
    privacyOpen: Boolean,
    privacySummary: BrowseXPrivacySummary,
    onDismissPrivacy: () -> Unit,
    onManageProtection: () -> Unit,
    onClearPrivacyData: (Long, Boolean) -> Unit,
    sitePermissionsOpen: Boolean,
    sitePermissions: List<com.browsex.app.storage.SitePermissionDecisionEntity>,
    onDismissSitePermissions: () -> Unit,
    onSetSitePermission: (String, String, String) -> Unit,
    onResetSitePermissions: () -> Unit,
    savedSessionsOpen: Boolean,
    savedSessions: List<com.browsex.app.storage.RoomSavedSession>,
    onDismissSavedSessions: () -> Unit,
    onRestoreSavedSession: (com.browsex.app.storage.RoomSavedSession) -> Unit,
    onOpenSavedSessionAlongside: (com.browsex.app.storage.RoomSavedSession) -> Unit,
    onRenameSavedSession: (String, String) -> Unit,
    onDeleteSavedSession: (String) -> Unit,
    onSaveCurrentSession: (String) -> Unit,
    onImportSavedSession: (String, String) -> Unit,
    onDeleteAllSavedSessions: () -> Unit,
    siteInfoOpen: Boolean,
    siteInfoState: BrowseXSiteInfoState,
    onDismissSiteInfo: () -> Unit,
    onChangeSiteInfoPermission: (GeckoSession.PermissionDelegate.ContentPermission, Int) -> Unit,
    onResetSiteInfoPermissions: () -> Unit,
    onClearSiteInfoData: () -> Unit,
    onSiteInfoMediaPermissionChange: (String, String) -> Unit,
    readerOpen: Boolean,
    readerState: BrowseXReaderState,
    onDismissReader: () -> Unit,
    onCopyReader: () -> Unit,
    recallOpen: Boolean,
    recallState: BrowseXRecallState,
    onDismissRecall: () -> Unit,
    onOpenRecall: (String) -> Unit,
    downloadsOpen: Boolean,
    downloadsState: BrowseXDownloadsState,
    onDismissDownloads: () -> Unit,
    onRefreshDownloads: () -> Unit,
    onPrimaryDownloadAction: (BrowseXDownloadItem) -> Unit,
    onShareDownload: (BrowseXDownloadItem) -> Unit,
    onDeleteDownloadFile: (BrowseXDownloadItem) -> Unit,
    onHomeSearch: (String) -> Unit,
    onHomeOpen: (String) -> Unit,
    onHomeBookmarks: () -> Unit,
    onHomeHistory: () -> Unit,
    onHomeDownloads: () -> Unit,
    onHomeSettings: () -> Unit,
    onHomePrivateTab: () -> Unit,
    onHomeCustomize: () -> Unit,
    uBlockSettingsOpen: Boolean,
    uBlockState: BrowseXUBlockState,
    onDismissUBlockSettings: () -> Unit,
    onInstallUBlock: (Boolean) -> Unit,
    onToggleUBlock: (Boolean) -> Unit,
    onUninstallUBlock: () -> Unit,
    onOpenUBlockSiteControls: () -> Unit
) {
    val primary = Color(state.accentColor)
    val surface = Color(state.panelColor)
    val field = Color(state.fieldColor)
    val ink = Color(state.inkColor)
    val muted = Color(state.mutedColor)
    val outline = Color(state.lineColor)
    val colorScheme = if (state.isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color.White,
            background = Color(state.canvasColor),
            surface = surface,
            onSurface = ink,
            onSurfaceVariant = muted,
            outline = outline
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            background = Color(state.canvasColor),
            surface = surface,
            onSurface = ink,
            onSurfaceVariant = muted,
            outline = outline
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(if (state.isHome) Color(state.canvasColor) else Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            val wide = maxWidth >= 600.dp
            val dockModifier = Modifier
                .widthIn(max = if (wide) 760.dp else maxWidth)
                .fillMaxWidth()

            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (state.reduceTransparency) surface.copy(alpha = 1f) else surface.copy(alpha = 0.86f),
                    border = BorderStroke(if (state.highContrast) 2.dp else 1.dp, outline),
                    shadowElevation = if (state.reduceTransparency) 2.dp else 5.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .padding(horizontal = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_browsex),
                            contentDescription = "BrowseX logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(30.dp)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 9.dp)
                        ) {
                            Text(
                                text = if (state.isPrivate) "BrowseX · Private" else "BrowseX",
                                color = ink,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = state.title.ifBlank { "Your browser" },
                                color = muted,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (state.isPrivate) {
                            Text(
                                text = "PRIVATE",
                                color = primary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                if (!state.dockAtTop) {
                    if (state.isHome) {
                        BrowseXHomeScreen(
                            state = state.homeState,
                            isDark = state.isDark,
                            onSearch = onHomeSearch,
                            onOpen = onHomeOpen,
                            onBookmarks = onHomeBookmarks,
                            onHistory = onHomeHistory,
                            onDownloads = onHomeDownloads,
                            onSettings = onHomeSettings,
                            onPrivateTab = onHomePrivateTab,
                            onCustomize = onHomeCustomize,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }

                Surface(
                    modifier = dockModifier.align(Alignment.CenterHorizontally),
                    shape = RoundedCornerShape(27.dp),
                    color = if (state.reduceTransparency) surface.copy(alpha = 1f) else surface.copy(alpha = 0.94f),
                    border = BorderStroke(if (state.highContrast) 2.dp else 1.dp, outline),
                    shadowElevation = if (state.reduceTransparency) 3.dp else 12.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp)
                    ) {
                        if (state.progress in 1..99) {
                            LinearProgressIndicator(
                                progress = state.progress / 100f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp),
                                color = primary,
                                trackColor = outline
                            )
                            Spacer(Modifier.height(7.dp))
                        }

                        var input by rememberSaveable { mutableStateOf(state.url) }
                        var editing by remember { mutableStateOf(false) }
                        var suggestions by remember { mutableStateOf(emptyList<AddressSuggestion>()) }
                        val focusManager = LocalFocusManager.current
                        val keyboard = LocalSoftwareKeyboardController.current

                        LaunchedEffect(state.url) {
                            if (!editing) input = state.url
                        }
                        LaunchedEffect(input, editing, state.isPrivate) {
                            if (!editing) {
                                suggestions = emptyList()
                            } else {
                                delay(110)
                                suggestions = runCatching {
                                    loadSuggestions(input.filterNot(Char::isISOControl).trim().take(256))
                                }.getOrDefault(emptyList()).take(6)
                            }
                        }

                        if (editing) {
                            val visibleSuggestions = buildList {
                                addAll(suggestions)
                                if (input.isNotBlank()) {
                                    add(
                                        AddressSuggestion(
                                            kind = AddressSuggestionKind.SEARCH,
                                            title = "Search the web",
                                            subtitle = input.trim().take(256),
                                            value = input.trim().take(256)
                                        )
                                    )
                                }
                            }.distinctBy { it.value }.take(7)
                            if (visibleSuggestions.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(19.dp),
                                    color = surface,
                                    border = BorderStroke(1.dp, outline),
                                    shadowElevation = 6.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 7.dp)
                                ) {
                                    LazyColumn(
                                        modifier = Modifier.heightIn(max = 260.dp)
                                    ) {
                                        items(visibleSuggestions, key = { "${it.kind.name}:${it.value}" }) { suggestion ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        onSelectSuggestion(suggestion)
                                                        input = suggestion.value
                                                        suggestions = emptyList()
                                                        focusManager.clearFocus()
                                                        keyboard?.hide()
                                                    }
                                                    .padding(horizontal = 13.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val icon = when (suggestion.kind) {
                                                    AddressSuggestionKind.OPEN_TAB -> Icons.Default.Tab
                                                    AddressSuggestionKind.BOOKMARK -> Icons.Default.BookmarkBorder
                                                    AddressSuggestionKind.HISTORY -> Icons.Default.History
                                                    AddressSuggestionKind.SEARCH -> Icons.Default.Search
                                                }
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = primary,
                                                    modifier = Modifier.size(21.dp)
                                                )
                                                Column(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(start = 11.dp)
                                                ) {
                                                    Text(
                                                        text = suggestion.title,
                                                        color = ink,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = suggestion.subtitle,
                                                        color = muted,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(19.dp),
                            color = field,
                            border = BorderStroke(if (state.highContrast) 2.dp else 1.dp, outline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .padding(horizontal = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (editing) {
                                            input = ""
                                        } else {
                                            onSiteInfo()
                                        }
                                    },
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        imageVector = if (editing) Icons.Default.Close else Icons.Default.Info,
                                        contentDescription = if (editing) "Clear address" else "Site information",
                                        tint = primary
                                    )
                                }

                                TextField(
                                    value = input,
                                    onValueChange = { input = it.take(2048) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .onFocusChanged { editing = it.isFocused },
                                    placeholder = {
                                        Text("Search or enter address", color = muted)
                                    },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = ink,
                                        textDirection = TextDirection.ContentOrLtr
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        imeAction = ImeAction.Go,
                                        keyboardType = KeyboardType.Text
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onGo = {
                                            onNavigate(input)
                                            focusManager.clearFocus()
                                            keyboard?.hide()
                                        },
                                        onSearch = {
                                            onNavigate(input)
                                            focusManager.clearFocus()
                                            keyboard?.hide()
                                        }
                                    ),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent,
                                        focusedTextColor = ink,
                                        unfocusedTextColor = ink,
                                        focusedPlaceholderColor = muted,
                                        unfocusedPlaceholderColor = muted,
                                        cursorColor = primary
                                    )
                                )

                                IconButton(
                                    onClick = {
                                        onReload()
                                        focusManager.clearFocus()
                                        keyboard?.hide()
                                    },
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Reload page",
                                        tint = ink
                                    )
                                }
                            }
                        }

                        if (state.showNavigationActions) {
                            Spacer(Modifier.height(5.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ChromeAction(
                                    label = "Back",
                                    enabled = state.canGoBack,
                                    onClick = onBack
                                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                                ChromeAction(
                                    label = "Forward",
                                    enabled = state.canGoForward,
                                    onClick = onForward
                                ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) }
                                ChromeAction(label = "Home", onClick = onHome) {
                                    Icon(Icons.Default.Home, contentDescription = null)
                                }
                                ChromeAction(label = "Bookmark", onClick = onBookmark) {
                                    Icon(Icons.Default.BookmarkBorder, contentDescription = null)
                                }
                                ChromeAction(label = "New tab", onClick = onNewTab) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                }
                                ChromeAction(label = "Tabs ${state.tabCount}", onClick = onOpenTabs) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Tab, contentDescription = null)
                                        Text(
                                            text = state.tabCount.coerceAtMost(99).toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = primary
                                        )
                                    }
                                }
                                if (state.uBlockAvailable) {
                                    ChromeAction(label = "Protection", onClick = onProtection) {
                                        Icon(Icons.Default.Security, contentDescription = null)
                                    }
                                }
                                ChromeAction(label = "Menu", onClick = onMenu) {
                                    Icon(Icons.Default.MoreHoriz, contentDescription = null)
                                }
                            }
                        }
                    }
                }

                if (state.dockAtTop) {
                    if (state.isHome) {
                        BrowseXHomeScreen(
                            state = state.homeState,
                            isDark = state.isDark,
                            onSearch = onHomeSearch,
                            onOpen = onHomeOpen,
                            onBookmarks = onHomeBookmarks,
                            onHistory = onHomeHistory,
                            onDownloads = onHomeDownloads,
                            onSettings = onHomeSettings,
                            onPrivateTab = onHomePrivateTab,
                            onCustomize = onHomeCustomize,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        if (menuOpen) {
            BrowseXMenuSheet(
                onDismiss = onDismissMenu,
                onAction = onMenuAction
            )
        }
        if (tabsOpen) {
            BrowseXTabsSheet(
                tabs = tabs,
                activeTab = activeTab,
                onDismiss = onDismissTabs,
                onSelect = onSelectTab,
                onClose = onCloseTab,
                onCreateTab = onCreateTab
            )
        }
        if (settingsOpen) {
            BrowseXSettingsSheet(
                settings = settings,
                androidNotificationsEnabled = androidNotificationsEnabled,
                onDismiss = onDismissSettings,
                onUpdateSettings = onUpdateSettings,
                onAction = onSettingsAction
            )
        }
        if (savedPagesOpen) {
            BrowseXSavedPagesScreen(
                state = savedPagesState,
                onDismiss = onDismissSavedPages,
                onOpen = onOpenSavedPage,
                onRemove = onRemoveSavedPage,
                onRenameBookmark = onRenameBookmark,
                onMoveBookmark = onMoveBookmark,
                onMoveWithinFolder = onMoveBookmarkWithinFolder,
                onCreateFolder = onCreateBookmarkFolder,
                onRenameFolder = onRenameBookmarkFolder,
                onDeleteFolder = onDeleteBookmarkFolder,
                onClearHistory = onClearSavedHistory,
                onShare = onShareSavedPage,
                onCopy = onCopySavedPage
            )
        }
        if (privacyOpen) {
            BrowseXPrivacySheet(
                summary = privacySummary,
                onDismiss = onDismissPrivacy,
                onManageProtection = onManageProtection,
                onClearData = onClearPrivacyData
            )
        }
        if (sitePermissionsOpen) {
            BrowseXSitePermissionsSheet(
                entries = sitePermissions,
                onDismiss = onDismissSitePermissions,
                onSetDecision = onSetSitePermission,
                onResetAll = onResetSitePermissions
            )
        }
        if (savedSessionsOpen) {
            BrowseXSavedSessionsScreen(
                sessions = savedSessions,
                onDismiss = onDismissSavedSessions,
                onRestore = onRestoreSavedSession,
                onOpenAlongside = onOpenSavedSessionAlongside,
                onRename = onRenameSavedSession,
                onDelete = onDeleteSavedSession,
                onSaveCurrent = onSaveCurrentSession,
                onImport = onImportSavedSession,
                onDeleteAll = onDeleteAllSavedSessions
            )
        }
        if (siteInfoOpen) {
            BrowseXSiteInfoScreen(
                state = siteInfoState,
                onDismiss = onDismissSiteInfo,
                onPermissionChange = onChangeSiteInfoPermission,
                onResetPermissions = onResetSiteInfoPermissions,
                onClearSiteData = onClearSiteInfoData,
                onMediaPermissionChange = onSiteInfoMediaPermissionChange
            )
        }
        if (readerOpen) {
            BrowseXReaderScreen(
                state = readerState,
                onDismiss = onDismissReader,
                onCopy = onCopyReader
            )
        }
        if (recallOpen) {
            BrowseXRecallScreen(
                state = recallState,
                onDismiss = onDismissRecall,
                onOpen = onOpenRecall
            )
        }
        if (downloadsOpen) {
            BrowseXDownloadsScreen(
                state = downloadsState,
                onDismiss = onDismissDownloads,
                onRefresh = onRefreshDownloads,
                onPrimaryAction = onPrimaryDownloadAction,
                onShare = onShareDownload,
                onDeleteFile = onDeleteDownloadFile
            )
        }
        if (uBlockSettingsOpen) {
            BrowseXUBlockSettingsScreen(
                state = uBlockState,
                onDismiss = onDismissUBlockSettings,
                onInstall = onInstallUBlock,
                onToggleEnabled = onToggleUBlock,
                onUninstall = onUninstallUBlock,
                onSiteControls = onOpenUBlockSiteControls
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseXTabsSheet(
    tabs: List<BrowserTab>,
    activeTab: BrowserTab?,
    onDismiss: () -> Unit,
    onSelect: (BrowserTab) -> Unit,
    onClose: (BrowserTab) -> Unit,
    onCreateTab: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Your tabs",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${tabs.size} open",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                androidx.compose.material3.FilledTonalButton(onClick = onCreateTab) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("New tab", modifier = Modifier.padding(start = 6.dp))
                }
            }
            var search by rememberSaveable { mutableStateOf("") }
            var filter by remember { mutableIntStateOf(0) }
            TextField(
                value = search,
                onValueChange = { search = it.take(128) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search tabs") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = filter == 0, onClick = { filter = 0 }, label = { Text("All") })
                FilterChip(selected = filter == 1, onClick = { filter = 1 }, label = { Text("Regular") })
                FilterChip(selected = filter == 2, onClick = { filter = 2 }, label = { Text("Private") })
            }
            val needle = search.trim().lowercase(Locale.ROOT)
            val visibleTabs = tabs.filter { tab ->
                (filter == 0 || (filter == 1 && !tab.privateMode) || (filter == 2 && tab.privateMode)) &&
                    (needle.isBlank() || tab.title.lowercase(Locale.ROOT).contains(needle) ||
                        tab.url.lowercase(Locale.ROOT).contains(needle))
            }
            Spacer(Modifier.height(12.dp))
            if (visibleTabs.isEmpty()) {
                Text(
                    text = "No open tabs",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 560.dp)) {
                    items(visibleTabs, key = { System.identityHashCode(it.session) }) { tab ->
                        val selected = tab === activeTab
                        val host = runCatching {
                            android.net.Uri.parse(tab.url).host?.removePrefix("www.")
                        }.getOrNull().orEmpty()
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onSelect(tab) },
                            shape = RoundedCornerShape(18.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            },
                            border = BorderStroke(
                                if (selected) 2.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 5.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = tab.title.ifBlank { "New tab" },
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (tab.privateMode) {
                                            Text(
                                                text = "PRIVATE",
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(start = 7.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = host.ifBlank { "New tab" },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (tab.progress in 1..99) {
                                        LinearProgressIndicator(
                                            progress = tab.progress / 100f,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 7.dp)
                                                .height(2.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onClose(tab) },
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close tab",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseXMenuSheet(
    onDismiss: () -> Unit,
    onAction: (BrowseXMenuAction) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 22.dp)
        ) {
            Text(
                text = "BrowseX tools",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyColumn(modifier = Modifier.heightIn(max = 560.dp)) {
                items(BrowseXMenuAction.values().toList(), key = { it.name }) { action ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { onAction(action) },
                        shape = RoundedCornerShape(15.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ) {
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChromeAction(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp).semantics { contentDescription = label }
    ) {
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides
                    if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            ) {
                icon()
            }
        }
    }
}

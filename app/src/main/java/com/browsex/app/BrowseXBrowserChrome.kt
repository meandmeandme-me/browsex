package com.browsex.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp

internal data class BrowseXChromeState(
    val url: String = "",
    val title: String = "BrowseX",
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
    onProtection: () -> Unit
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
                .background(Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            val wide = maxWidth >= 600.dp
            val dockModifier = Modifier
                .widthIn(max = if (wide) 760.dp else maxWidth)
                .fillMaxWidth()
                .align(if (state.dockAtTop) Alignment.TopCenter else Alignment.BottomCenter)

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
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
                        Surface(
                            shape = CircleShape,
                            color = primary.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, primary.copy(alpha = 0.35f)),
                            modifier = Modifier.size(29.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "B",
                                    color = primary,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
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

                Surface(
                    modifier = dockModifier,
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
                        val focusManager = LocalFocusManager.current
                        val keyboard = LocalSoftwareKeyboardController.current

                        LaunchedEffect(state.url) {
                            if (!editing) input = state.url
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
                                    cursorBrush = SolidColor(primary),
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
                                        if (state.progress in 1..99) onReload() else onReload()
                                        focusManager.clearFocus()
                                        keyboard?.hide()
                                    },
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        imageVector = if (state.progress in 1..99) Icons.Default.Close else Icons.Default.Refresh,
                                        contentDescription = if (state.progress in 1..99) "Stop loading" else "Reload page",
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
        modifier = Modifier.size(40.dp)
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

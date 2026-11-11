package com.browsex.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.selection.SelectionContainer

internal data class BrowseXReaderState(
    val title: String,
    val sourceUrl: String,
    val articleText: String
)

@Composable
internal fun BrowseXReaderScreen(
    state: BrowseXReaderState,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    val context = LocalContext.current
    fun t(en: String, ar: String = en) = BrowseXStrings.text(context, en, ar)
    var textSize by rememberSaveable { mutableFloatStateOf(18f) }
    val host = runCatching { android.net.Uri.parse(state.sourceUrl).host?.removePrefix("www.") }.getOrNull().orEmpty()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 17.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "BrowseX · " + t("Reader", "وضع القراءة"),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            host,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) { Text(t("Close", "إغلاق")) }
                }

                Text(
                    state.title.ifBlank { t("Article", "مقال") },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 20.dp, bottom = 10.dp)
                )

                SelectionContainer(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 18.dp)
                    ) {
                        Text(
                            text = state.articleText,
                            style = TextStyle(
                                fontFamily = FontFamily.Serif,
                                fontSize = textSize.sp,
                                lineHeight = (textSize * 1.62f).sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textDirection = TextDirection.Content
                            ),
                            textAlign = TextAlign.Start
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { textSize = (textSize - 1f).coerceAtLeast(14f) }) {
                        Text("A−")
                    }
                    TextButton(onClick = { textSize = (textSize + 1f).coerceAtMost(30f) }) {
                        Text("A+")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onCopy) { Text(t("Copy article", "نسخ المقال")) }
                }
            }
        }
    }
}

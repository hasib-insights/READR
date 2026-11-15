package com.readr.app.ui.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readr.app.domain.model.ReaderFont
import com.readr.app.domain.model.ReadingPrefs
import com.readr.app.domain.model.StyledSpan
import com.readr.app.domain.model.TextBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

fun fontFamilyOf(font: ReaderFont): FontFamily = when (font) {
    ReaderFont.SERIF -> FontFamily.Serif
    ReaderFont.SANS -> FontFamily.SansSerif
    ReaderFont.MONO -> FontFamily.Monospace
    ReaderFont.DEFAULT -> FontFamily.Default
}

/** Maps a flat block index to (chapterIndex, blockIndex) using chapter start offsets. */
fun locateBlock(cumStarts: IntArray, index: Int): Pair<Int, Int> {
    if (cumStarts.isEmpty()) return 0 to index
    var chapter = 0
    for (i in cumStarts.indices) {
        if (index >= cumStarts[i]) chapter = i else break
    }
    return chapter to (index - cumStarts[chapter])
}

fun buildAnnotated(spans: List<StyledSpan>): AnnotatedString = buildAnnotatedString {
    for (span in spans) {
        withStyle(
            SpanStyle(
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                textDecoration = if (span.underline) TextDecoration.Underline else null,
            )
        ) { append(span.text) }
    }
}

/**
 * The unified text reader for EPUB, DOCX and TXT: paper background with grain,
 * adjustable typography and margins, chapter navigation and lazy block loading
 * (TXT streams paragraphs from disk on demand).
 */
@Composable
fun TextContent(
    vm: ReaderViewModel,
    content: ReaderContent.Text,
    prefs: ReadingPrefs,
    listState: LazyListState,
    textColor: Color,
    onToggleChrome: () -> Unit,
) {
    val document = content.document
    val cumStarts = remember(document) {
        IntArray(document.chapters.size).also { starts ->
            var acc = 0
            for (i in document.chapters.indices) {
                starts[i] = acc
                acc += document.chapters[i].blockCount
            }
        }
    }
    val font = fontFamilyOf(prefs.font)
    val fontSize = prefs.fontSizeSp.sp
    val lineHeight = (prefs.fontSizeSp * prefs.lineSpacing).sp
    val margin = prefs.marginDp.dp

    // Resume the saved position once.
    LaunchedEffect(content) {
        val target = cumStarts[content.initialChapter] + content.initialParagraph
        if (target > 0 && target < document.totalBlocks) {
            listState.scrollToItem(target)
        }
    }

    // Persist the reading position as the user scrolls.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collectLatest { index ->
                delay(500)
                val (chapter, block) = locateBlock(cumStarts, index)
                vm.saveTextPosition(chapter, block)
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onToggleChrome() }) },
        contentPadding = PaddingValues(
            start = margin, end = margin, top = 72.dp, bottom = 140.dp,
        ),
    ) {
        items(count = document.totalBlocks, key = { it }) { index ->
            val (chapter, blockIndex) = locateBlock(cumStarts, index)
            val block = document.chapters[chapter].blockAt(blockIndex)
            BlockItem(
                block = block,
                font = font,
                fontSize = prefs.fontSizeSp,
                lineHeight = lineHeight,
                textColor = textColor,
                imageLoader = { src -> vm.loadImageBytes(src) },
                modifier = Modifier.padding(bottom = if (block is TextBlock.Paragraph) 10.dp else 12.dp),
            )
        }
    }
}

@Composable
private fun BlockItem(
    block: TextBlock,
    font: FontFamily,
    fontSize: Int,
    lineHeight: TextUnit,
    textColor: Color,
    imageLoader: (String) -> ByteArray?,
    modifier: Modifier = Modifier,
) {
    when (block) {
        is TextBlock.Paragraph -> {
            if (block.imageSource != null) {
                EpubImage(
                    loader = imageLoader,
                    source = block.imageSource!!,
                    modifier = modifier.fillMaxWidth(),
                )
                return
            }
            if (block.headingLevel > 0) {
                val headingScale = when (block.headingLevel) {
                    1 -> 1.62f
                    2 -> 1.42f
                    3 -> 1.26f
                    4 -> 1.15f
                    else -> 1.05f
                }
                Text(
                    text = buildAnnotated(block.spans),
                    color = textColor,
                    fontFamily = FontFamily.Serif,
                    fontSize = (fontSize * headingScale).sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = (fontSize * headingScale * 1.25f).sp,
                    modifier = modifier.padding(top = 12.dp, bottom = 2.dp),
                )
            } else if (block.spans.all { it.text.isBlank() }) {
                // Blank line between paragraphs
                Box(modifier = modifier.height((fontSize / 2).dp))
            } else if (block.isListItem) {
                Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("•", color = textColor, fontSize = fontSize.sp, fontFamily = font)
                    Text(
                        text = buildAnnotated(block.spans),
                        color = textColor,
                        fontSize = fontSize.sp,
                        lineHeight = lineHeight,
                        fontFamily = font,
                    )
                }
            } else {
                Text(
                    text = buildAnnotated(block.spans),
                    color = textColor,
                    fontSize = fontSize.sp,
                    lineHeight = lineHeight,
                    fontFamily = font,
                    modifier = modifier,
                )
            }
        }

        is TextBlock.Table -> {
            Surface(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, textColor.copy(alpha = 0.3f)),
                color = Color.Transparent,
            ) {
                Column(Modifier.padding(4.dp)) {
                    for (row in block.rows) {
                        Row {
                            for (cell in row) {
                                Text(
                                    cell,
                                    color = textColor,
                                    fontSize = (fontSize * 0.9f).sp,
                                    fontFamily = font,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpubImage(
    loader: (String) -> ByteArray?,
    source: String,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(source) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(source) { mutableStateOf(false) }
    LaunchedEffect(source) {
        val decoded = withContext(Dispatchers.IO) {
            runCatching { loader(source)?.let { decodeSampledBitmap(it, 1024) } }.getOrNull()
        }
        bitmap = decoded
        if (decoded == null) failed = true
    }
    when {
        bitmap != null -> Image(
            bitmap = bitmap!!,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.FillWidth,
        )
        failed -> Text(
            "[image]",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        else -> Box(modifier.height(120.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp))
        }
    }
}

package com.readr.app.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.readr.app.di.AppContainer
import com.readr.app.domain.model.ReadrDocument
import com.readr.app.domain.model.ReadingPrefs
import com.readr.app.domain.model.TextDocument
import com.readr.app.domain.model.plainText
import com.readr.app.ui.components.ConfirmDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The Reading Room: a distraction-free, chrome-on-tap reading surface for all
 * four formats, with paper styling, brightness gesture, bookmarks and settings.
 */
@Composable
fun ReaderScreen(documentId: Long, onBack: () -> Unit) {
    val container = AppContainer.Local.current
    val vm: ReaderViewModel = viewModel(
        key = "reader_$documentId",
        factory = viewModelFactory { initializer { ReaderViewModel(container, documentId) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()

    when (val s = state) {
        ReaderUiState.Loading -> Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        is ReaderUiState.Error -> ErrorContent(s, vm, onBack)
        is ReaderUiState.Ready -> ReaderReady(vm, s.document, s.content, onBack)
    }
}

@Composable
private fun ReaderReady(
    vm: ReaderViewModel,
    document: ReadrDocument,
    content: ReaderContent,
    onBack: () -> Unit,
) {
    val prefs by vm.readingPrefs.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showChapters by rememberSaveable { mutableStateOf(false) }

    // System back closes the reader's own panels first (the sheets handle it
    // too; this is the safety net that keeps back predictable) and only then
    // lets the NavHost pop the reader.
    BackHandler(enabled = showSettings || showChapters) {
        showSettings = false
        showChapters = false
    }

    val isPdf = content is ReaderContent.Pdf
    val paperBg = Color(prefs.customBackground ?: prefs.paperTheme.bg)
    val paperText = Color(prefs.customTextColor ?: prefs.paperTheme.text)
    val chromeBg = if (isPdf) Color(0xF21C1A17) else paperBg.copy(alpha = 0.97f)
    val chromeText = if (isPdf) Color(0xFFD8CFBC) else paperText

    // ---- shared navigation state ----
    val pagerState = rememberPagerState(
        initialPage = (content as? ReaderContent.Pdf)?.initialPage ?: 0,
        pageCount = { (content as? ReaderContent.Pdf)?.pageCount ?: 0 },
    )
    val listState = rememberLazyListState()
    val zoom = remember { mutableFloatStateOf(1f) }

    val textDocument = (content as? ReaderContent.Text)?.document
    val cumStarts = remember(textDocument) {
        textDocument?.let { doc ->
            IntArray(doc.chapters.size).also { starts ->
                var acc = 0
                for (i in doc.chapters.indices) {
                    starts[i] = acc
                    acc += doc.chapters[i].blockCount
                }
            }
        }
    }
    val firstVisible = listState.firstVisibleItemIndex
    val textPos = locateBlock(cumStarts ?: IntArray(0), firstVisible)
    val chapterTitle = textDocument?.chapters?.getOrNull(textPos.first)?.title
    val textProgress = textDocument?.let { (firstVisible + 1f) / it.totalBlocks } ?: 0f

    // ---- brightness control ----
    val activity = LocalContext.current.findActivity()
    var brightness by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.attributes = activity?.window?.attributes?.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
    val applyBrightness: (Float) -> Unit = { raw ->
        val value = raw.coerceIn(0.02f, 1f)
        brightness = value
        activity?.window?.attributes = activity?.window?.attributes?.apply {
            screenBrightness = value
        }
        if (prefs.systemBrightnessMode && activity != null &&
            Settings.System.canWrite(activity)
        ) {
            runCatching {
                Settings.System.putInt(
                    activity.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    (value * 255).toInt(),
                )
            }
        }
    }

    // Keep zoom sane across page turns.
    LaunchedEffect(pagerState.currentPage) { zoom.floatValue = 1f }

    Box(
        Modifier
            .fillMaxSize()
            .background(if (isPdf) Color(0xFF27241F) else paperBg)
    ) {
        when (content) {
            is ReaderContent.Pdf -> PdfContent(
                vm = vm,
                content = content,
                prefs = prefs,
                pagerState = pagerState,
                zoom = zoom,
                onTap = { chromeVisible = !chromeVisible },
                onZoomAnchor = {
                    zoom.floatValue = if (zoom.floatValue > 1.05f) 1f else 2.4f
                },
            )

            is ReaderContent.Text -> TextContent(
                vm = vm,
                content = content,
                prefs = prefs,
                listState = listState,
                textColor = paperText,
                onToggleChrome = { chromeVisible = !chromeVisible },
            )
        }

        // Warm tint sits above content, below the chrome.
        if (prefs.warmFilter) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x26FFB84D))
            )
        }

        // ---- edge brightness gesture ----
        // Composed BELOW the chrome (and inset past both chrome strips) so it
        // can never swallow taps on the back button or the bottom controls.
        BrightnessGestureZone(
            prefs = prefs,
            onAdjust = applyBrightness,
            current = { brightness },
            modifier = Modifier.align(Alignment.CenterStart),
        )

        // ---- top chrome ----
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            Surface(color = chromeBg, shadowElevation = 4.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = chromeText,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            document.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = chromeText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        document.author?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = chromeText.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    val bookmarked = when (content) {
                        is ReaderContent.Pdf -> bookmarks.any { it.page == pagerState.currentPage }
                        is ReaderContent.Text -> bookmarks.any {
                            it.chapterIndex == textPos.first && it.paragraphIndex == textPos.second
                        }
                    }
                    IconButton(onClick = {
                        when (content) {
                            is ReaderContent.Pdf -> vm.togglePageBookmark(pagerState.currentPage)
                            is ReaderContent.Text -> {
                                val preview = runCatching {
                                    textDocument?.chapters?.getOrNull(textPos.first)
                                        ?.blockAt(textPos.second)?.plainText().orEmpty()
                                }.getOrDefault("")
                                vm.toggleTextBookmark(textPos.first, textPos.second, preview)
                            }
                        }
                    }) {
                        Icon(
                            if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = if (bookmarked) "Remove bookmark" else "Add bookmark",
                            tint = chromeText,
                        )
                    }
                }
            }
        }

        // ---- bottom chrome ----
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Surface(color = chromeBg, shadowElevation = 8.dp) {
                Column(
                    Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 10.dp, top = 6.dp)
                ) {
                    when (content) {
                        is ReaderContent.Pdf -> {
                            if (content.pageCount > 1) {
                                Slider(
                                    value = (pagerState.currentPage + 1f),
                                    onValueChange = { value ->
                                        scope.launch { pagerState.scrollToPage(value.toInt() - 1) }
                                    },
                                    valueRange = 1f..content.pageCount.toFloat(),
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(onClick = {
                                    zoom.floatValue = (zoom.floatValue - 0.5f).coerceIn(1f, 5f)
                                }) {
                                    Icon(Icons.Filled.ZoomOut, "Zoom out", tint = chromeText)
                                }
                                IconButton(onClick = {
                                    zoom.floatValue = (zoom.floatValue + 0.5f).coerceIn(1f, 5f)
                                }) {
                                    Icon(Icons.Filled.ZoomIn, "Zoom in", tint = chromeText)
                                }
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Text(
                                        "Page ${pagerState.currentPage + 1} of ${content.pageCount}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = chromeText,
                                    )
                                }
                                IconButton(onClick = { showSettings = true }) {
                                    Icon(Icons.Filled.Tune, "Reading settings", tint = chromeText)
                                }
                            }
                        }

                        is ReaderContent.Text -> {
                            val document2 = content.document
                            if (document2.totalBlocks > 1) {
                                Slider(
                                    value = firstVisible.toFloat(),
                                    onValueChange = { value ->
                                        scope.launch {
                                            listState.scrollToItem(
                                                value.toInt().coerceIn(0, document2.totalBlocks - 1)
                                            )
                                        }
                                    },
                                    valueRange = 0f..(document2.totalBlocks - 1).coerceAtLeast(1).toFloat(),
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(onClick = {
                                    val target = (textPos.first - 1).coerceAtLeast(0)
                                    cumStarts?.let { scope.launch { listState.scrollToItem(it[target]) } }
                                }) {
                                    Icon(Icons.Filled.KeyboardArrowLeft, "Previous chapter", tint = chromeText)
                                }
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .clickable { showChapters = true },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            chapterTitle ?: document2.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = chromeText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "${(textProgress * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = chromeText.copy(alpha = 0.65f),
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    val target = (textPos.first + 1)
                                        .coerceAtMost((cumStarts?.size ?: 1) - 1)
                                    cumStarts?.let { scope.launch { listState.scrollToItem(it[target]) } }
                                }) {
                                    Icon(Icons.Filled.KeyboardArrowRight, "Next chapter", tint = chromeText)
                                }
                                IconButton(onClick = { showSettings = true }) {
                                    Icon(Icons.Filled.Tune, "Reading settings", tint = chromeText)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        ReaderSettingsSheet(
            prefs = prefs,
            vm = vm,
            isPdf = isPdf,
            brightness = brightness,
            onBrightnessChange = applyBrightness,
            onDismiss = { showSettings = false },
        )
    }

    if (showChapters && textDocument != null && cumStarts != null) {
        ChapterPickerSheet(
            document = textDocument,
            cumStarts = cumStarts,
            currentChapter = textPos.first,
            onSelect = { index ->
                scope.launch {
                    listState.scrollToItem(cumStarts[index])
                    showChapters = false
                }
            },
            onDismiss = { showChapters = false },
        )
    }
}

@Composable
private fun BrightnessGestureZone(
    prefs: ReadingPrefs,
    onAdjust: (Float) -> Unit,
    current: () -> Float?,
    modifier: Modifier = Modifier,
) {
    var indicator by remember { mutableStateOf<Float?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier
            .fillMaxHeight()
            .width(48.dp)
            // Stay clear of the top chrome (status bar + bar) and the bottom
            // chrome so the brightness drag never starts on a control.
            .padding(top = 104.dp, bottom = 132.dp)
            .pointerInput(prefs.brightnessGestureEnabled, prefs.gestureSensitivity) {
                if (!prefs.brightnessGestureEnabled) return@pointerInput
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val height = size.height.toFloat().coerceAtLeast(1f)
                        val base = current() ?: 0.5f
                        val next = (base + (-dragAmount / height) * 0.85f * prefs.gestureSensitivity)
                            .coerceIn(0.02f, 1f)
                        onAdjust(next)
                        indicator = next
                    },
                    onDragEnd = { scope.launch { delay(800); indicator = null } },
                    onDragCancel = { scope.launch { delay(800); indicator = null } },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(visible = indicator != null, enter = fadeIn(), exit = fadeOut()) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xE61C1A17),
            ) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.Brightness6,
                        contentDescription = null,
                        tint = Color(0xFFD8CFBC),
                    )
                    Text(
                        "${((indicator ?: 0f) * 100).roundToInt()}%",
                        color = Color(0xFFD8CFBC),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterPickerSheet(
    document: TextDocument,
    cumStarts: IntArray,
    currentChapter: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Chapters",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            for (i in document.chapters.indices) {
                val selected = i == currentChapter
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(i) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(
                        "${i + 1}.  ${document.chapters[i].title}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorContent(
    error: ReaderUiState.Error,
    vm: ReaderViewModel,
    onBack: () -> Unit,
) {
    var confirmRemove by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Text(
            "Couldn't open this document",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            error.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (error.fileMissing) {
            OutlinedButton(
                onClick = { confirmRemove = true },
                modifier = Modifier.padding(top = 20.dp),
            ) { Text("Remove from library") }
        }
        Button(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
            Text("Back to library")
        }
    }
    if (confirmRemove) {
        ConfirmDialog(
            title = "Remove from library",
            text = "READR can no longer reach this file. Remove it from your library?",
            confirmLabel = "Remove",
            danger = true,
            onConfirm = { vm.removeDocument { onBack() } },
            onDismiss = { confirmRemove = false },
        )
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

package com.readr.app.ui.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.readr.app.domain.model.ReadingPrefs
import com.readr.app.engine.pdf.PdfReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Invert-with-preserve filter for a comfortable dark PDF reading mode. */
private fun darkFilter(): ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 1f,
            0f, -1f, 0f, 0f, 1f,
            0f, 0f, -1f, 0f, 1f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)

/**
 * PDF reading surface: framework PdfRenderer page by page in a pager, with
 * double-tap zoom, zoom buttons, pan while zoomed, and an optional dark
 * rendering filter. The original PDF appearance is preserved by default.
 */
@Composable
fun PdfContent(
    vm: ReaderViewModel,
    content: ReaderContent.Pdf,
    prefs: ReadingPrefs,
    pagerState: PagerState,
    zoom: State<Float>,
    onTap: () -> Unit,
    onZoomAnchor: (Offset) -> Unit,
) {
    val pan = remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(pagerState.currentPage) {
        vm.savePage(pagerState.currentPage, content.pageCount)
        pan.value = Offset.Zero
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportWpx = constraints.maxWidth
        val viewportHpx = constraints.maxHeight
        val pageSize = remember(content.reader) { content.reader.firstPageSize }
        val aspect = pageSize.first.toFloat() / pageSize.second.toFloat()
        val targetWidth = if (prefs.pdfFitWidth) {
            (viewportWpx * 1.5f).toInt().coerceIn(600, 2000)
        } else {
            (viewportHpx * aspect * 1.5f).toInt().coerceIn(600, 2000)
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = zoom.value <= 1.02f,
            pageSpacing = 12.dp,
            key = { it },
        ) { page ->
            PdfPageView(
                reader = content.reader,
                page = page,
                targetWidth = targetWidth,
                dark = prefs.pdfDarkFilter,
                scale = zoom.value,
                pan = pan.value,
                onTap = onTap,
                onDoubleTap = onZoomAnchor,
                onPan = { delta ->
                    val s = zoom.value
                    val maxX = viewportWpx * (s - 1f) / 2f
                    val maxY = viewportHpx * (s - 1f) / 2f
                    pan.value = Offset(
                        (pan.value.x + delta.x).coerceIn(-maxX, maxX),
                        (pan.value.y + delta.y).coerceIn(-maxY, maxY),
                    )
                },
            )
        }
    }
}

@Composable
private fun PdfPageView(
    reader: PdfReader,
    page: Int,
    targetWidth: Int,
    dark: Boolean,
    scale: Float,
    pan: Offset,
    onTap: () -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onPan: (Offset) -> Unit,
) {
    var bitmap by remember(page, targetWidth) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(page) { mutableStateOf(false) }

    LaunchedEffect(page, targetWidth) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { reader.renderPage(page, targetWidth) }.getOrNull()
        }
        if (bitmap == null) failed = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = pan.x
                translationY = pan.y
            }
            .pointerInput(scale) {
                if (scale > 1.02f) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onPan(dragAmount)
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { onDoubleTap(it) },
                )
            },
    ) {
        val bmp = bitmap
        when {
            bmp != null -> Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Page ${page + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillWidth,
                colorFilter = if (dark) darkFilter() else null,
            )

            failed -> Text(
                "This page could not be rendered.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center),
            )

            else -> CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp)
            )
        }
    }
}

/** Shared decoder for EPUB inline images, sampled to keep memory bounded. */
fun decodeSampledBitmap(bytes: ByteArray, maxDim: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxDim || bounds.outHeight / (sample * 2) >= maxDim) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    return bitmap.asImageBitmap()
}

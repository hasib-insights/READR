package com.readr.app.ui.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

/** Zoom bounds shared by pinch gestures, the toolbar buttons and double-tap. */
internal const val PDF_MIN_ZOOM = 1f
internal const val PDF_MAX_ZOOM = 5f

/**
 * PDF reading surface: framework PdfRenderer page by page in a pager, with
 * pinch zoom in AND out, double-tap zoom, zoom buttons, pan while zoomed, and
 * an optional dark rendering filter. The original PDF appearance is preserved
 * by default.
 */
@Composable
fun PdfContent(
    vm: ReaderViewModel,
    content: ReaderContent.Pdf,
    prefs: ReadingPrefs,
    pagerState: PagerState,
    zoom: MutableState<Float>,
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

        // Keep pan valid for every zoom change (buttons, double-tap, pinch):
        // zooming out re-clamps the offset and fully zoomed out snaps the page
        // back into place so the pager takes over cleanly again.
        LaunchedEffect(zoom.value) {
            val z = zoom.value
            if (z <= PDF_MIN_ZOOM + 0.02f) {
                pan.value = Offset.Zero
            } else {
                val maxX = viewportWpx * (z - 1f) / 2f
                val maxY = viewportHpx * (z - 1f) / 2f
                pan.value = Offset(
                    pan.value.x.coerceIn(-maxX, maxX),
                    pan.value.y.coerceIn(-maxY, maxY),
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = zoom.value <= PDF_MIN_ZOOM + 0.02f,
            pageSpacing = 12.dp,
            key = { it },
        ) { page ->
            PdfPageView(
                reader = content.reader,
                page = page,
                targetWidth = targetWidth,
                dark = prefs.pdfDarkFilter,
                zoom = zoom,
                pan = pan,
                onTap = onTap,
                onDoubleTap = onZoomAnchor,
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
    zoom: MutableState<Float>,
    pan: MutableState<Offset>,
    onTap: () -> Unit,
    onDoubleTap: (Offset) -> Unit,
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
                scaleX = zoom.value
                scaleY = zoom.value
                translationX = pan.value.x
                translationY = pan.value.y
            }
            .pointerInput(Unit) {
                // Pinch zoom in AND out + pan, anchored on the pinch centroid.
                // Zoom and pan are live MutableState so rapid gesture events
                // never build on stale values.
                detectTransformGestures(panZoomLock = false) { centroid, gesturePan, gestureZoom, _ ->
                    val oldScale = zoom.value
                    val newScale = (oldScale * gestureZoom).coerceIn(PDF_MIN_ZOOM, PDF_MAX_ZOOM)

                    // Fully zoomed out: reset exactly, so scrolling and page
                    // navigation resume without a stuck offset.
                    if (newScale <= PDF_MIN_ZOOM + 0.02f) {
                        zoom.value = PDF_MIN_ZOOM
                        pan.value = Offset.Zero
                        return@detectTransformGestures
                    }

                    // 1. Finger drag in screen space.
                    var newPan = pan.value + gesturePan

                    // 2. Re-anchor the zoom around the pinch centroid so the
                    //    content under the fingers stays under the fingers.
                    val ratio = newScale / oldScale
                    val anchor = centroid - Offset(size.width / 2f, size.height / 2f)
                    newPan = anchor * (1f - ratio) + newPan * ratio

                    // 3. Keep the page covering the viewport while zoomed.
                    val maxX = size.width * (newScale - 1f) / 2f
                    val maxY = size.height * (newScale - 1f) / 2f
                    pan.value = Offset(
                        newPan.x.coerceIn(-maxX, maxX),
                        newPan.y.coerceIn(-maxY, maxY),
                    )
                    zoom.value = newScale
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

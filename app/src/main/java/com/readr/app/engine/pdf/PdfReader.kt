package com.readr.app.engine.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * PDF engine built on the Android framework [PdfRenderer]: streaming page
 * rendering with a small LRU cache, so large PDFs never load fully into
 * memory. Not thread-safe by itself — all access is synchronized.
 */
class PdfReader(file: File) : AutoCloseable {

    private val pfd: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)

    private val cache = object : LinkedHashMap<Pair<Int, Int>, Bitmap>(8, 0.8f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Int, Int>, Bitmap>): Boolean {
            // Do NOT recycle here: the UI may still reference the bitmap.
            // Bitmaps are reclaimed by the GC; recycle() is only called on close().
            return size > MAX_CACHED_PAGES
        }
    }

    val pageCount: Int = renderer.pageCount

    /** (width, height) of the first page, used for fit-page calculations. */
    val firstPageSize: Pair<Int, Int> by lazy {
        renderer.openPage(0).use { page -> page.width to page.height }
    }

    /** Renders [index] at the requested bitmap width, cached per (page, width). */
    @Synchronized
    fun renderPage(index: Int, targetWidth: Int): Bitmap {
        val key = index to targetWidth
        cache[key]?.let { return it }
        renderer.openPage(index).use { page ->
            val width = targetWidth.coerceIn(64, 2400)
            val scale = width.toFloat() / page.width
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            val matrix = Matrix().apply { setScale(scale, scale) }
            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            cache[key] = bitmap
            return bitmap
        }
    }

    @Synchronized
    override fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
        cache.values.forEach { runCatching { it.recycle() } }
        cache.clear()
    }

    private companion object {
        const val MAX_CACHED_PAGES = 4
    }
}

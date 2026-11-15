package com.readr.app.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.readr.app.R
import com.readr.app.domain.model.DocumentFormat
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Generates attractive fallback covers for documents without embedded
 * artwork: a deterministic book-cloth palette derived from the title,
 * paper grain, spine band, typographic title/author and a format badge.
 */
object CoverGenerator {

    private data class Palette(val bg: Int, val band: Int, val text: Int, val accent: Int)

    private val palettes = listOf(
        Palette(0xFF8FA47F.toInt(), 0xFF6E835F.toInt(), 0xFF1F2E1B.toInt(), 0xFFFAF3E3.toInt()), // sage
        Palette(0xFFD9C7A7.toInt(), 0xFFB99F76.toInt(), 0xFF4A3520.toInt(), 0xFFFAF3E3.toInt()), // tan
        Palette(0xFFB96B4C.toInt(), 0xFF95503A.toInt(), 0xFF2E1B12.toInt(), 0xFFF7EDE2.toInt()), // clay
        Palette(0xFF77808B.toInt(), 0xFF5D6671.toInt(), 0xFF1E242B.toInt(), 0xFFEFF3F6.toInt()), // slate
        Palette(0xFF63534A.toInt(), 0xFF4C3F38.toInt(), 0xFFE8DDCF.toInt(), 0xFFC9BBA9.toInt()), // coffee
        Palette(0xFF9C8AA5.toInt(), 0xFF7C6C85.toInt(), 0xFF2A2130.toInt(), 0xFFF3EEF6.toInt()), // mauve
    )

    fun generate(target: File, title: String, author: String?, format: DocumentFormat) {
        val w = 600
        val h = 900
        val palette = palettes[abs(title.hashCode()) % palettes.size]

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.bg)

        // Subtle paper grain across the whole cover
        val grain = loadGrain()
        if (grain != null) {
            val paint = Paint().apply {
                shader = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                alpha = 46
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        }

        // Spine band on the left
        Paint().apply { color = palette.band }.let {
            canvas.drawRect(0f, 0f, 18f, h.toFloat(), it)
        }
        Paint().apply { color = 0x33000000 }.let {
            canvas.drawRect(18f, 0f, 21f, h.toFloat(), it)
        }

        // Decorative rules
        Paint().apply { color = palette.accent; strokeWidth = 3f }.let {
            canvas.drawLine(44f, 88f, w - 44f, 88f, it)
            canvas.drawLine(44f, h - 208f, w - 44f, h - 208f, it)
        }

        val safeTitle = title.take(120)

        val titlePaint = TextPaint().apply {
            color = palette.text
            textSize = when {
                safeTitle.length > 44 -> 40f
                safeTitle.length > 24 -> 48f
                else -> 56f
            }
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val titleLayout = StaticLayout.Builder
            .obtain(safeTitle, 0, safeTitle.length, titlePaint, w - 96)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.12f)
            .build()
        canvas.save()
        canvas.translate(48f, 132f)
        titleLayout.draw(canvas)
        canvas.restore()

        if (!author.isNullOrBlank()) {
            val authorPaint = TextPaint().apply {
                color = palette.text
                textSize = 30f
                typeface = Typeface.SERIF
                isAntiAlias = true
            }
            val authorLayout = StaticLayout.Builder
                .obtain(author.take(60), 0, author.take(60).length, authorPaint, w - 96)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
            canvas.save()
            canvas.translate(48f, (132 + titleLayout.height + 30).coerceAtMost(h - 330).toFloat())
            authorLayout.draw(canvas)
            canvas.restore()
        }

        // Format badge
        Paint().apply { color = palette.accent; isAntiAlias = true }.let {
            canvas.drawRoundRect(44f, h - 152f, 44f + 152f, h - 88f, 22f, 22f, it)
        }
        TextPaint().apply {
            color = palette.bg
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }.let {
            canvas.drawText(format.label, 44f + 76f, h - 105f, it)
        }

        TextPaint().apply {
            color = palette.text
            textSize = 26f
            typeface = Typeface.SERIF
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }.let {
            canvas.drawText("READR", w - 44f, h - 105f, it)
        }

        FileOutputStream(target).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        bitmap.recycle()
    }

    private var grainCache: Bitmap? = null

    private fun loadGrain(): Bitmap? {
        grainCache?.let { return it }
        val grain = BitmapFactory.decodeResource(ResourcesHolder.resources, R.drawable.paper_grain)
        grainCache = grain
        return grain
    }

    /** Initialized once by the importer with the application context. */
    private object ResourcesHolder {
        var resources: android.content.res.Resources? = null
    }

    fun init(context: Context) {
        ResourcesHolder.resources = context.resources
    }
}

package com.readr.app.domain

import com.readr.app.domain.model.EYE_COMFORT_BACKGROUNDS
import com.readr.app.domain.model.EYE_COMFORT_TEXT_COLORS
import com.readr.app.domain.model.PaperTheme
import com.readr.app.domain.model.MIN_TEXT_CONTRAST
import com.readr.app.domain.model.contrastRatio
import com.readr.app.domain.model.readableTextColor
import com.readr.app.domain.model.relativeLuminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Page-colour readability: every paper pairs a readable text colour, and the
 * Eye Comfort custom palettes can always produce a readable combination.
 */
class ColorContrastTest {

    @Test
    fun `white and black have the maximum contrast ratio`() {
        assertEquals(21f, contrastRatio(0xFFFFFFFF, 0xFF000000), 0.01f)
    }

    @Test
    fun `relative luminance is ordered black - gray - white`() {
        val black = relativeLuminance(0xFF000000)
        val gray = relativeLuminance(0xFF808080)
        val white = relativeLuminance(0xFFFFFFFF)
        assertTrue(black < gray && gray < white)
        assertEquals(0f, black, 1e-4f)
        assertEquals(1f, white, 1e-4f)
    }

    @Test
    fun `contrast ratio is symmetric`() {
        assertEquals(
            contrastRatio(0xFFFAF3E3, 0xFF3A3128),
            contrastRatio(0xFF3A3128, 0xFFFAF3E3),
            1e-6f,
        )
    }

    @Test
    fun `every paper theme pairs a readable text colour`() {
        for (theme in PaperTheme.entries) {
            assertTrue(
                "${theme.name}: text unreadable on its own paper",
                contrastRatio(theme.bg, theme.text) >= 4.5f,
            )
        }
    }

    @Test
    fun `readable text colour is kept when contrast is already fine`() {
        assertNull(readableTextColor(0xFFFAF3E3, 0xFF3A3128, EYE_COMFORT_TEXT_COLORS))
    }

    @Test
    fun `dark text on dark paper is flagged and repaired`() {
        // WARM_CREAM text on the dark reading background is unreadable.
        val ratio = contrastRatio(0xFF201E1A, 0xFF3A3128)
        assertTrue(ratio < MIN_TEXT_CONTRAST)
        assertEquals(
            0xFFD8CFBCL,
            readableTextColor(0xFF201E1A, 0xFF3A3128, EYE_COMFORT_TEXT_COLORS),
        )
    }

    @Test
    fun `light text on light paper is flagged and repaired`() {
        val repaired = readableTextColor(0xFFFEFEFC, 0xFFD8CFBC, EYE_COMFORT_TEXT_COLORS)
        assertNotNull(repaired)
        assertTrue(contrastRatio(0xFFFEFEFC, repaired!!) > contrastRatio(0xFFFEFEFC, 0xFFD8CFBC))
    }

    @Test
    fun `every custom background has a readable palette text colour`() {
        for (bg in EYE_COMFORT_BACKGROUNDS) {
            val best = EYE_COMFORT_TEXT_COLORS.maxOf { contrastRatio(bg, it) }
            assertTrue(
                "no readable text colour for background 0x%X".format(bg),
                best >= MIN_TEXT_CONTRAST,
            )
        }
    }

    @Test
    fun `auto-repair always improves contrast above the minimum`() {
        for (bg in EYE_COMFORT_BACKGROUNDS) {
            for (text in EYE_COMFORT_TEXT_COLORS) {
                val repaired = readableTextColor(bg, text, EYE_COMFORT_TEXT_COLORS)
                if (repaired != null) {
                    val before = contrastRatio(bg, text)
                    val after = contrastRatio(bg, repaired)
                    assertTrue(after > before)
                    assertTrue(after >= MIN_TEXT_CONTRAST)
                }
            }
        }
    }
}

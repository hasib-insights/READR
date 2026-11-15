package com.readr.app.domain

import com.readr.app.domain.model.ReadingPosition
import com.readr.app.ui.reader.locateBlock
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingPositionTest {

    @Test
    fun `serializes and deserializes`() {
        val json = Json { encodeDefaults = true }
        val position = ReadingPosition(page = 12)
        val encoded = json.encodeToString(ReadingPosition.serializer(), position)
        val decoded = json.decodeFromString(ReadingPosition.serializer(), encoded)
        assertEquals(position, decoded)

        val textPosition = ReadingPosition(chapterIndex = 3, paragraphIndex = 41)
        val encoded2 = json.encodeToString(ReadingPosition.serializer(), textPosition)
        assertEquals(textPosition, json.decodeFromString(ReadingPosition.serializer(), encoded2))
    }

    @Test
    fun `locates blocks inside chapter ranges`() {
        // Chapters of sizes 10, 5, 8 → starts 0, 10, 15
        val starts = intArrayOf(0, 10, 15)
        assertEquals(0 to 0, locateBlock(starts, 0))
        assertEquals(0 to 9, locateBlock(starts, 9))
        assertEquals(1 to 0, locateBlock(starts, 10))
        assertEquals(2 to 3, locateBlock(starts, 18))
        assertEquals(2 to 0, locateBlock(starts, 15))
    }

    @Test
    fun `handles empty chapter table`() {
        assertEquals(0 to 7, locateBlock(IntArray(0), 7))
    }
}

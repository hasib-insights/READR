package com.readr.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Where the user stopped reading.
 *
 * - [page] is the 0-based PDF page index.
 * - [chapterIndex] / [paragraphIndex] address text documents (EPUB, DOCX, TXT)
 *   at the granularity the reader scrolls by (paragraph blocks inside chapters).
 */
@Serializable
data class ReadingPosition(
    val page: Int = 0,
    val chapterIndex: Int = 0,
    val paragraphIndex: Int = 0,
) {
    companion object {
        val START = ReadingPosition()
    }
}

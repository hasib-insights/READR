package com.readr.app.domain.model

/**
 * The unified text model every text engine (EPUB, DOCX, TXT) produces and the
 * text reader renders. Documents may back blocks lazily (TXT reads paragraphs
 * straight from the file on demand), so chapters expose a count plus a
 * [TextChapter.blockAt] accessor instead of a materialised list.
 */
data class StyledSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
)

sealed interface TextBlock {
    /**
     * A paragraph of styled runs. [headingLevel] 1..6 marks headings, 0 is body
     * text. [isListItem] prefixes a bullet in rendering. [imageSource] non-null
     * marks an inline image resource (resolved by the engine that produced it).
     */
    data class Paragraph(
        val spans: List<StyledSpan>,
        val headingLevel: Int = 0,
        val isListItem: Boolean = false,
        val imageSource: String? = null,
    ) : TextBlock

    /** A simple table: rows of cell strings (DOCX support, kept minimal). */
    data class Table(val rows: List<List<String>>) : TextBlock
}

class TextChapter(
    val id: String,
    val title: String,
    val blockCount: Int,
    val blockAt: (Int) -> TextBlock,
)

data class TextDocument(
    val title: String,
    val author: String?,
    val chapters: List<TextChapter>,
    /** Approximate word count; null when the engine cannot estimate it. */
    val wordCount: Long?,
    /** Releases lazily held resources (file handles, zip handles). */
    val closeCallback: (() -> Unit)? = null,
) {
    val totalBlocks: Int = chapters.sumOf { it.blockCount }

    fun blocksBefore(chapterIndex: Int, blockIndex: Int): Int =
        chapters.take(chapterIndex.coerceIn(0, chapters.size)).sumOf { it.blockCount } + blockIndex

    fun close() {
        closeCallback?.invoke()
    }
}

/** Plain text of a block, used for bookmark previews and progress estimates. */
fun TextBlock.plainText(): String = when (this) {
    is TextBlock.Paragraph -> spans.joinToString("") { it.text }
    is TextBlock.Table -> rows.joinToString("\n") { row -> row.joinToString("  |  ") }
}

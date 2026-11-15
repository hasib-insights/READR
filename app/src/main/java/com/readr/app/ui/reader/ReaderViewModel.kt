package com.readr.app.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readr.app.data.db.BookmarkEntity
import com.readr.app.data.prefs.PreferencesRepository
import com.readr.app.di.AppContainer
import com.readr.app.domain.model.DocumentFormat
import com.readr.app.domain.model.ReadrDocument
import com.readr.app.domain.model.ReadingPosition
import com.readr.app.domain.model.ReadingPrefs
import com.readr.app.domain.model.TextDocument
import com.readr.app.engine.docx.DocxEngine
import com.readr.app.engine.epub.EpubEngine
import com.readr.app.engine.epub.EpubImageResolver
import com.readr.app.engine.pdf.PdfReader
import com.readr.app.engine.txt.TxtEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Error(val message: String, val fileMissing: Boolean) : ReaderUiState
    data class Ready(val document: ReadrDocument, val content: ReaderContent) : ReaderUiState
}

sealed interface ReaderContent {
    data class Pdf(
        val reader: PdfReader,
        val pageCount: Int,
        val initialPage: Int,
    ) : ReaderContent

    data class Text(
        val document: TextDocument,
        val initialChapter: Int,
        val initialParagraph: Int,
    ) : ReaderContent
}

/**
 * Reading Room ViewModel: opens documents with the right engine, restores the
 * last reading position, and persists position/progress as the user reads.
 */
class ReaderViewModel(
    private val container: AppContainer,
    private val documentId: Long,
) : ViewModel() {

    private val repo = container.documentRepository
    val prefsRepo: PreferencesRepository = container.preferencesRepository

    val readingPrefs = prefsRepo.readingPrefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReadingPrefs())

    private val _state = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    val bookmarks: StateFlow<List<BookmarkEntity>> = repo.observeBookmarks(documentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val closeables = ArrayList<AutoCloseable>()
    private var imageResolver: EpubImageResolver? = null
    private var textDocument: TextDocument? = null
    private var totalBlocks = 0

    init {
        viewModelScope.launch { open() }
    }

    private suspend fun open() {
        val doc = repo.get(documentId)
        if (doc == null) {
            _state.value = ReaderUiState.Error("This document is no longer in the library.", true)
            return
        }
        val file = File(doc.filePath)
        if (!file.exists()) {
            _state.value = ReaderUiState.Error(
                "The file is missing or was moved. You can remove it from the library and import it again.",
                true,
            )
            return
        }
        repo.markOpened(documentId)

        _state.value = runCatching {
            when (doc.format) {
                DocumentFormat.PDF -> {
                    val reader = PdfReader(file)
                    closeables.add(reader)
                    val page = doc.position.page.coerceIn(0, (reader.pageCount - 1).coerceAtLeast(0))
                    ReaderUiState.Ready(doc, ReaderContent.Pdf(reader, reader.pageCount, page))
                }

                DocumentFormat.EPUB -> {
                    val text = container.cachedText(file.path) ?: run {
                        val book = EpubEngine.parse(file)
                        container.putText(file.path, book.document)
                        book.document
                    }
                    imageResolver = EpubImageResolver(file).also { closeables.add(it) }
                    openText(doc, text)
                }

                DocumentFormat.DOCX -> {
                    val text = container.cachedText(file.path) ?: run {
                        val book = DocxEngine.parse(file)
                        container.putText(file.path, book.document)
                        book.document
                    }
                    openText(doc, text)
                }

                DocumentFormat.TXT -> {
                    val text = container.cachedText(file.path)
                        ?: TxtEngine.parse(file).also { container.putText(file.path, it) }
                    openText(doc, text)
                }
            }
        }.getOrElse { e ->
            ReaderUiState.Error(
                "Could not open this document: ${e.message?.take(120) ?: "unknown error"}",
                false,
            )
        }
    }

    private fun openText(doc: ReadrDocument, text: TextDocument): ReaderUiState.Ready {
        textDocument = text
        totalBlocks = text.totalBlocks
        val chapter = doc.position.chapterIndex
            .coerceIn(0, (text.chapters.size - 1).coerceAtLeast(0))
        val paragraph = doc.position.paragraphIndex
            .coerceIn(0, (text.chapters.getOrNull(chapter)?.blockCount ?: 1) - 1)
        return ReaderUiState.Ready(doc, ReaderContent.Text(text, chapter, paragraph))
    }

    /** Inline image bytes for EPUB chapters, resolved off the UI thread. */
    fun loadImageBytes(source: String): ByteArray? = runCatching {
        imageResolver?.image(source)
    }.getOrNull()

    fun savePage(page: Int, pageCount: Int) {
        val progress = if (pageCount > 0) (page + 1f) / pageCount else 0f
        viewModelScope.launch {
            repo.saveReadingState(documentId, ReadingPosition(page = page), progress)
        }
    }

    fun saveTextPosition(chapterIndex: Int, paragraphIndex: Int) {
        val doc = textDocument ?: return
        val before = doc.blocksBefore(chapterIndex, paragraphIndex)
        val progress = if (totalBlocks > 0) ((before + paragraphIndex + 1f) / totalBlocks) else 0f
        viewModelScope.launch {
            repo.saveReadingState(
                documentId,
                ReadingPosition(chapterIndex = chapterIndex, paragraphIndex = paragraphIndex),
                progress.coerceIn(0f, 1f),
            )
        }
    }

    fun togglePageBookmark(page: Int) {
        viewModelScope.launch {
            repo.togglePageBookmark(documentId, page, "Page ${page + 1}")
        }
    }

    fun toggleTextBookmark(chapterIndex: Int, paragraphIndex: Int, preview: String) {
        viewModelScope.launch {
            repo.toggleTextBookmark(documentId, chapterIndex, paragraphIndex, preview)
        }
    }

    fun removeDocument(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.remove(documentId)
            onDone()
        }
    }

    override fun onCleared() {
        closeables.forEach { runCatching { it.close() } }
    }
}

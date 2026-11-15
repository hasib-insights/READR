package com.readr.app.data.repo

import androidx.room.withTransaction
import com.readr.app.data.db.BookmarkEntity
import com.readr.app.data.db.ReadrDatabase
import com.readr.app.data.db.toDomain
import com.readr.app.domain.model.ReadrDocument
import com.readr.app.domain.model.ReadingPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Repository over documents + bookmarks, plus the file management for the
 * imported copies and generated covers.
 */
class DocumentRepository(
    database: ReadrDatabase,
    private val documentsDir: File,
    private val coversDir: File,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val documentDao = database.documentDao()
    private val bookmarkDao = database.bookmarkDao()

    fun observeAll(): Flow<List<ReadrDocument>> =
        documentDao.observeAll().map { list -> list.mapNotNull { it.toDomain(json) } }

    fun observeById(id: Long): Flow<ReadrDocument?> =
        documentDao.observeById(id).map { it?.toDomain(json) }

    suspend fun get(id: Long): ReadrDocument? = documentDao.getById(id)?.toDomain(json)

    suspend fun insert(entity: com.readr.app.data.db.DocumentEntity): Long =
        documentDao.insert(entity)

    suspend fun updateCoverPath(id: Long, coverPath: String) {
        val entity = documentDao.getById(id) ?: return
        documentDao.update(entity.copy(coverPath = coverPath))
    }

    suspend fun saveReadingState(id: Long, position: ReadingPosition, progress: Float) {
        documentDao.saveReadingState(
            id,
            System.currentTimeMillis(),
            json.encodeToString(ReadingPosition.serializer(), position),
            progress.coerceIn(0f, 1f),
        )
    }

    suspend fun markOpened(id: Long) = documentDao.markOpened(id, System.currentTimeMillis())

    suspend fun setFavorite(id: Long, favorite: Boolean) =
        documentDao.setFavorite(id, favorite)

    /** Removes a document from the library together with its files and bookmarks. */
    suspend fun remove(id: Long): ReadrDocument? {
        val doc = get(id) ?: return null
        bookmarkDao.deleteForDocument(id)
        documentDao.deleteById(id)
        File(doc.filePath).delete()
        doc.coverPath?.let { File(it).delete() }
        return doc
    }

    suspend fun count(): Int = documentDao.count()

    // --- Bookmarks ---

    fun observeBookmarks(documentId: Long): Flow<List<BookmarkEntity>> =
        bookmarkDao.observeForDocument(documentId)

    suspend fun addBookmark(entity: BookmarkEntity): Long = bookmarkDao.insert(entity)

    suspend fun deleteBookmark(id: Long) = bookmarkDao.deleteById(id)

    /** Returns true if the page is bookmarked after the call. */
    suspend fun togglePageBookmark(documentId: Long, page: Int, preview: String): Boolean {
        val existing = bookmarkDao.findPageBookmark(documentId, page)
        if (existing != null) {
            bookmarkDao.deleteById(existing.id)
            return false
        }
        bookmarkDao.insert(
            BookmarkEntity(
                documentId = documentId, page = page,
                chapterIndex = null, paragraphIndex = null,
                preview = preview.take(160), createdAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    /** Returns true if the paragraph is bookmarked after the call. */
    suspend fun toggleTextBookmark(
        documentId: Long, chapter: Int, paragraph: Int, preview: String
    ): Boolean {
        val existing = bookmarkDao.findTextBookmark(documentId, chapter, paragraph)
        if (existing != null) {
            bookmarkDao.deleteById(existing.id)
            return false
        }
        bookmarkDao.insert(
            BookmarkEntity(
                documentId = documentId, page = null,
                chapterIndex = chapter, paragraphIndex = paragraph,
                preview = preview.take(160), createdAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    // --- Files ---

    fun coverFile(id: Long, ext: String = "jpg") = File(coversDir, "cover_$id.$ext")

    fun newDocumentFile(ext: String): File = File(documentsDir, "${java.util.UUID.randomUUID()}.$ext")

    fun clearCovers(): Int {
        val files = coversDir.listFiles() ?: return 0
        var n = 0
        files.forEach { if (it.delete()) n++ }
        return n
    }
}

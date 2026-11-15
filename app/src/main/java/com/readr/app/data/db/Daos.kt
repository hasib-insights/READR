package com.readr.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Insert
    suspend fun insert(doc: DocumentEntity): Long

    @Update
    suspend fun update(doc: DocumentEntity)

    @Query("SELECT * FROM documents ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observeById(id: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getById(id: Long): DocumentEntity?

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun count(): Int

    @Query(
        "UPDATE documents SET lastOpenedAt = :timestamp, positionJson = :positionJson, progress = :progress " +
            "WHERE id = :id"
    )
    suspend fun saveReadingState(id: Long, timestamp: Long, positionJson: String, progress: Float)

    @Query("UPDATE documents SET lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun markOpened(id: Long, timestamp: Long)

    @Query("UPDATE documents SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface BookmarkDao {

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bookmarks WHERE documentId = :documentId")
    suspend fun deleteForDocument(documentId: Long)

    @Query("SELECT * FROM bookmarks WHERE documentId = :documentId ORDER BY createdAt DESC")
    fun observeForDocument(documentId: Long): Flow<List<BookmarkEntity>>

    @Query(
        "SELECT * FROM bookmarks WHERE documentId = :documentId AND page = :page " +
            "AND chapterIndex IS NULL LIMIT 1"
    )
    suspend fun findPageBookmark(documentId: Long, page: Int): BookmarkEntity?

    @Query(
        "SELECT * FROM bookmarks WHERE documentId = :documentId AND chapterIndex = :chapter " +
            "AND paragraphIndex = :paragraph LIMIT 1"
    )
    suspend fun findTextBookmark(documentId: Long, chapter: Int, paragraph: Int): BookmarkEntity?
}

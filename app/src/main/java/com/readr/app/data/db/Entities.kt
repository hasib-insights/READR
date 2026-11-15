package com.readr.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String?,
    val format: String,
    val fileName: String,
    val filePath: String,
    val coverPath: String?,
    val sizeBytes: Long,
    val pageCount: Int?,
    val wordCount: Long?,
    val addedAt: Long,
    val lastOpenedAt: Long?,
    val progress: Float = 0f,
    val favorite: Boolean = false,
    /** JSON-encoded ReadingPosition. */
    val positionJson: String = "{}",
)

@Entity(tableName = "bookmarks", indices = [Index("documentId")])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    /** PDF bookmarks use [page]; text bookmarks use chapter + paragraph. */
    val page: Int?,
    val chapterIndex: Int?,
    val paragraphIndex: Int?,
    val preview: String,
    val createdAt: Long,
)

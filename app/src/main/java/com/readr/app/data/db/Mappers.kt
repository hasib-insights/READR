package com.readr.app.data.db

import com.readr.app.domain.model.DocumentFormat
import com.readr.app.domain.model.ReadrDocument
import com.readr.app.domain.model.ReadingPosition
import kotlinx.serialization.json.Json

internal fun DocumentEntity.toDomain(json: Json): ReadrDocument? {
    val format = runCatching { DocumentFormat.valueOf(format) }.getOrNull() ?: return null
    val position = runCatching {
        if (positionJson.isBlank() || positionJson == "{}") ReadingPosition.START
        else json.decodeFromString<ReadingPosition>(positionJson)
    }.getOrDefault(ReadingPosition.START)
    return ReadrDocument(
        id = id,
        title = title,
        author = author,
        format = format,
        filePath = filePath,
        coverPath = coverPath,
        sizeBytes = sizeBytes,
        pageCount = pageCount,
        wordCount = wordCount,
        addedAt = addedAt,
        lastOpenedAt = lastOpenedAt,
        progress = progress,
        favorite = favorite,
        position = position,
    )
}

package com.readr.app.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.room.Room
import com.readr.app.data.db.ReadrDatabase
import com.readr.app.data.prefs.PreferencesRepository
import com.readr.app.data.repo.DocumentRepository
import com.readr.app.domain.model.TextDocument
import com.readr.app.importer.DocumentImporter
import java.io.File

/**
 * Simple manual dependency container. READR is small enough that a DI
 * framework would be ceremony; everything is created once here.
 */
class AppContainer(private val appContext: Context) {

    val database: ReadrDatabase = Room.databaseBuilder(
        appContext, ReadrDatabase::class.java, "readr.db"
    ).build()

    val preferencesRepository = PreferencesRepository(appContext)

    val documentsDir: File = File(appContext.filesDir, "documents").apply { mkdirs() }
    val coversDir: File = File(appContext.filesDir, "covers").apply { mkdirs() }
    val logsDir: File = File(appContext.filesDir, "logs").apply { mkdirs() }

    val documentRepository = DocumentRepository(database, documentsDir, coversDir)
    val importer = DocumentImporter(appContext, documentRepository, documentsDir, coversDir)

    // Recently parsed text documents are cached so reopening is instant.
    // TXT documents hold open file handles, so eviction must close them.
    private val textCache = object : LinkedHashMap<String, TextDocument>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TextDocument>): Boolean {
            if (size > 4) {
                eldest.value.close()
                return true
            }
            return false
        }
    }

    @Synchronized
    fun cachedText(path: String): TextDocument? = textCache[path]

    @Synchronized
    fun putText(path: String, doc: TextDocument) {
        textCache[path] = doc
    }

    @Synchronized
    fun evictText(path: String) {
        textCache.remove(path)
    }

    companion object {
        val Local = staticCompositionLocalOf<AppContainer> {
            error("AppContainer not provided")
        }
    }
}

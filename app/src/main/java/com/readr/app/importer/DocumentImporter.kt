package com.readr.app.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.readr.app.data.db.DocumentEntity
import com.readr.app.data.repo.DocumentRepository
import com.readr.app.domain.model.DocumentFormat
import com.readr.app.engine.docx.DocxEngine
import com.readr.app.engine.epub.EpubEngine
import com.readr.app.engine.pdf.PdfReader
import com.readr.app.engine.txt.TxtEngine
import com.readr.app.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

data class ParsedDocument(
    val title: String?,
    val author: String?,
    val coverBytes: ByteArray?,
    val pageCount: Int?,
    val wordCount: Long?,
)

/**
 * Imports documents through the Storage Access Framework: copies the file
 * into app-private storage (so the library keeps working after the source
 * moves), extracts metadata, and generates a cover.
 */
class DocumentImporter(
    private val context: Context,
    private val repository: DocumentRepository,
    private val documentsDir: File,
    private val coversDir: File,
) {

    data class ImportOutcome(val importedCount: Int, val failures: List<String>)

    suspend fun import(uris: List<Uri>): ImportOutcome = withContext(Dispatchers.IO) {
        CoverGenerator.init(context)
        val failures = ArrayList<String>()
        var imported = 0
        for (uri in uris) {
            val name = queryDisplayName(uri) ?: "Document"
            try {
                if (importOne(uri, name) != null) imported++
                else failures.add("$name: unsupported format")
            } catch (e: Exception) {
                failures.add("$name: ${readableError(e)}")
            }
        }
        ImportOutcome(imported, failures)
    }

    private suspend fun importOne(uri: Uri, displayName: String): Long? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val header = readHeader(resolver, uri)
        var format = FileUtils.sniffFormat(header, displayName)

        val dest = repository.newDocumentFile(format?.extensions?.first() ?: "bin")
        resolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return@withContext null

        // Ambiguous zip (PK header, unknown name): inspect the archive entries.
        if (format == null) {
            format = runCatching {
                ZipFile(dest).use { zip ->
                    FileUtils.sniffZipFormat(zip.entries().asSequence().map { it.name }.toList(), displayName)
                }
            }.getOrNull()
        }
        if (format == null) {
            dest.delete()
            throw IllegalArgumentException("Unsupported document format")
        }

        val parsed: ParsedDocument = when (format) {
            DocumentFormat.PDF -> parsePdf(dest)
            DocumentFormat.EPUB -> {
                val book = EpubEngine.parse(dest)
                ParsedDocument(book.title, book.author, book.coverBytes, null, book.document.wordCount)
            }
            DocumentFormat.DOCX -> {
                val book = DocxEngine.parse(dest)
                ParsedDocument(book.title, book.author, null, null, book.document.wordCount)
            }
            DocumentFormat.TXT -> {
                val doc = TxtEngine.parse(dest)
                doc.close()
                ParsedDocument(null, null, null, null, doc.wordCount)
            }
        }

        val title = parsed.title?.takeIf { it.isNotBlank() }
            ?: displayName.substringBeforeLast('.').replace('_', ' ').trim().ifEmpty { "Untitled" }

        val entity = DocumentEntity(
            title = title,
            author = parsed.author,
            format = format.name,
            fileName = displayName,
            filePath = dest.path,
            coverPath = null,
            sizeBytes = dest.length(),
            pageCount = parsed.pageCount,
            wordCount = parsed.wordCount,
            addedAt = System.currentTimeMillis(),
            lastOpenedAt = null,
        )
        val id = repository.insert(entity)

        val coverFile = repository.coverFile(id)
        val coverOk = writeCover(coverFile, parsed.coverBytes, title, parsed.author, format)
        if (coverOk) repository.updateCoverPath(id, coverFile.path)

        id
    }

    private fun parsePdf(file: File): ParsedDocument {
        PdfReader(file).use { reader ->
            val coverBitmap = runCatching {
                reader.renderPage(0, 480)
            }.getOrNull()
            val coverBytes = coverBitmap?.let { bmp ->
                val out = java.io.ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
                out.toByteArray()
            }
            return ParsedDocument(null, null, coverBytes, reader.pageCount, null)
        }
    }

    /** Saves embedded cover bytes, or generates a designed cover when absent. */
    private fun writeCover(
        target: File,
        coverBytes: ByteArray?,
        title: String,
        author: String?,
        format: DocumentFormat,
    ): Boolean = runCatching {
        if (coverBytes != null && coverBytes.size > 128) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size, bounds)
            if (bounds.outWidth > 0) {
                val sample = maxOf(1, bounds.outWidth / 800)
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size, options)
                if (bitmap != null) {
                    target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                    bitmap.recycle()
                    return@runCatching true
                }
            }
        }
        CoverGenerator.generate(target, title, author, format)
        target.length() > 128
    }.getOrDefault(false)

    private fun readHeader(resolver: android.content.ContentResolver, uri: Uri): ByteArray {
        return resolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(64)
            val n = runCatching { input.read(buf) }.getOrDefault(0)
            if (n > 0) buf.copyOf(n) else ByteArray(0)
        } ?: ByteArray(0)
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            } ?: uri.lastPathSegment
        }.getOrNull()
    }

    private fun readableError(e: Exception): String = when (e) {
        is SecurityException -> "file access denied"
        is java.io.FileNotFoundException -> "file not found"
        is IllegalArgumentException -> e.message ?: "unsupported format"
        is java.io.IOException -> e.message ?: "read error"
        else -> e.message?.take(80) ?: "import failed"
    }
}

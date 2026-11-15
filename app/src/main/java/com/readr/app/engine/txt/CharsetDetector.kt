package com.readr.app.engine.txt

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Charset detection for TXT files: BOM first, then a strict UTF-8 validation,
 * then a GBK attempt for CJK content, with Windows-1252 as the final
 * never-failing fallback.
 */
object CharsetDetector {

    private val GBK: Charset? = runCatching { Charset.forName("GBK") }.getOrNull()
    private val CP1252: Charset = runCatching { Charset.forName("windows-1252") }.getOrNull()
        ?: Charsets.ISO_8859_1

    fun detect(file: File): Charset {
        val bytes = file.inputStream().use { input ->
            val b = ByteArray(8192)
            val n = runCatching { input.read(b) }.getOrDefault(0)
            if (n > 0) b.copyOf(n) else ByteArray(0)
        }
        return detect(bytes)
    }

    fun detect(bytes: ByteArray): Charset {
        if (bytes.isEmpty()) return Charsets.UTF_8

        // Byte order marks
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return Charsets.UTF_8
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return Charsets.UTF_16LE
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return Charsets.UTF_16BE
        }

        // Strict UTF-8 validation — ASCII and proper UTF-8 both pass.
        if (isValidCharset(bytes, Charsets.UTF_8)) return Charsets.UTF_8

        // Non-UTF-8 high bytes: try GBK; accept only when decoding succeeds AND
        // the result is dominated by CJK characters (avoids misreading short
        // Windows-1252 text as GBK).
        if (GBK != null && hasHighBytes(bytes)) {
            val decoded = decodeOrNull(bytes, GBK)
            if (decoded != null && isCjkDominated(decoded)) return GBK
        }

        // Latin fallback (never fails).
        return CP1252
    }

    private fun hasHighBytes(bytes: ByteArray): Boolean =
        bytes.any { it < 0 }

    private fun isValidCharset(bytes: ByteArray, charset: Charset): Boolean =
        decodeOrNull(bytes, charset) != null

    private fun decodeOrNull(bytes: ByteArray, charset: Charset): String? {
        return try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            null
        }
    }

    private fun isCjkDominated(text: String): Boolean {
        if (text.length < 2) return false
        val cjk = text.count { isCjk(it) }
        return cjk * 10 >= text.length * 4 // ≥ 40% CJK characters
    }

    private fun isCjk(ch: Char): Boolean =
        ch.code in 0x4E00..0x9FFF || ch.code in 0x3040..0x30FF ||
            ch.code in 0xAC00..0xD7AF || ch.code in 0xFF00..0xFFEF
}

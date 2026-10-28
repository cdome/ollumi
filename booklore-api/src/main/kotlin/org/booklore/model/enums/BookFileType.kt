package org.booklore.model.enums

import java.util.*

enum class BookFileType(val extensions: Set<String>) {
    PDF(setOf("pdf")),
    EPUB(setOf("epub")),
    CBX(setOf("cbz", "cbr", "cb7")),
    FB2(setOf("fb2")),
    MOBI(setOf("mobi")),
    AZW3(setOf("azw3", "azw")),
    AUDIOBOOK(setOf("m4b", "m4a", "mp3", "opus"));

    fun supports(extension: String): Boolean = extensions.contains(extension.lowercase())

    companion object {
        @JvmStatic
        fun fromExtension(extension: String): Optional<BookFileType> {
            val ext = extension.lowercase()
            return Arrays.stream(entries.toTypedArray())
                .filter { it.supports(ext) }
                .findFirst()
        }
    }
}

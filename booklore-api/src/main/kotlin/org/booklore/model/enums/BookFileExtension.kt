package org.booklore.model.enums

import java.util.*

enum class BookFileExtension(val extension: String, val type: BookFileType) {
    PDF("pdf", BookFileType.PDF),
    EPUB("epub", BookFileType.EPUB),
    CBZ("cbz", BookFileType.CBX),
    CBR("cbr", BookFileType.CBX),
    CB7("cb7", BookFileType.CBX),
    MOBI("mobi", BookFileType.MOBI),
    AZW3("azw3", BookFileType.AZW3),
    AZW("azw", BookFileType.AZW3),
    FB2("fb2", BookFileType.FB2),
    M4B("m4b", BookFileType.AUDIOBOOK),
    M4A("m4a", BookFileType.AUDIOBOOK),
    MP3("mp3", BookFileType.AUDIOBOOK),
    OPUS("opus", BookFileType.AUDIOBOOK);

    companion object {
        @JvmStatic
        fun fromFileName(fileName: String): Optional<BookFileExtension> {
            val lower = fileName.lowercase()
            return Arrays.stream(entries.toTypedArray())
                .filter { lower.endsWith("." + it.extension) }
                .findFirst()
        }
    }
}

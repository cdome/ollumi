package org.booklore.model.dto.request

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotNull
import org.booklore.model.dto.progress.AudiobookProgress
import org.booklore.model.dto.progress.CbxProgress
import org.booklore.model.dto.progress.EpubProgress
import org.booklore.model.dto.progress.PdfProgress
import java.time.Instant

data class ReadProgressRequest(
    @field:NotNull
    val bookId: Long? = null,

    val fileProgress: BookFileProgress? = null,

    @Deprecated("")
    val epubProgress: EpubProgress? = null,

    @Deprecated("")
    val pdfProgress: PdfProgress? = null,

    @Deprecated("")
    val cbxProgress: CbxProgress? = null,

    @Deprecated("")
    val audiobookProgress: AudiobookProgress? = null,

    val dateFinished: Instant? = null
) {
    @AssertTrue(message = "At least one progress field must be provided")
    fun isProgressValid(): Boolean {
        return fileProgress != null || epubProgress != null || pdfProgress != null ||
                cbxProgress != null || audiobookProgress != null || dateFinished != null
    }
}

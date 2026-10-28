package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator

data class PdfBookInfo constructor(
        val pageCount: Int? = null,
        val outline: List<PdfOutlineItem?>? = null
)

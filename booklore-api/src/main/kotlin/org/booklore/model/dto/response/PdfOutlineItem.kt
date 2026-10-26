package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator

data class PdfOutlineItem constructor(
        val title: String? = null,
        val pageNumber: Int? = null,
        val children: List<PdfOutlineItem?>? = null
)

package org.booklore.model.dto.response.ranobedbapi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonCreator

@JsonIgnoreProperties(ignoreUnknown = true)
data class RanobedbSearchResponse constructor(
        val books: List<Book?>? = null,
        val count: String? = null,
        val currentPage: Int? = null,
        val totalPages: Int? = null
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Book constructor(
            val id: Int? = null
    )
}

package org.booklore.model.dto.response.comicvineapi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonCreator

@JsonIgnoreProperties(ignoreUnknown = true)
data class ComicvineApiResponse constructor(
        val error: String? = null,
        val limit: Int? = null,
        val offset: Int? = null,
        @JsonProperty("number_of_page_results")
        val numberOfPageResults: Int? = null,
        @JsonProperty("number_of_total_results")
        val numberOfTotalResults: Int? = null,
        @JsonProperty("status_code")
        val statusCode: Int? = null,
        val results: List<Comic?>? = null,
        val version: String? = null
)

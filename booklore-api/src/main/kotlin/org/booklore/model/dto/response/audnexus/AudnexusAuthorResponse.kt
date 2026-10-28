package org.booklore.model.dto.response.audnexus

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonCreator

@JsonIgnoreProperties(ignoreUnknown = true)
data class AudnexusAuthorResponse constructor(
        val asin: String? = null,
        val name: String? = null,
        val description: String? = null,
        @JsonProperty("image")
        val imageUrl: String? = null,
        val region: String? = null,
        val genres: List<Genre?>? = null,
        val similar: List<SimilarAuthor?>? = null
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Genre constructor(
            val asin: String? = null,
            val name: String? = null,
            val type: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SimilarAuthor constructor(
            val asin: String? = null,
            val name: String? = null
    )
}

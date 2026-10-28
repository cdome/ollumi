package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class EpubSpineItem constructor(
        val idref: String? = null,
        val href: String? = null,
        val mediaType: String? = null,
        @get:JvmName("isLinear")
        @get:JsonProperty("linear")
        val linear: Boolean? = null
)

package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator

data class EpubManifestItem constructor(
        val id: String? = null,
        val href: String? = null,
        val mediaType: String? = null,
        val properties: List<String?>? = null,
        val size: Long? = null
)

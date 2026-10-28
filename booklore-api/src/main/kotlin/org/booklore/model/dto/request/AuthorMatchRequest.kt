package org.booklore.model.dto.request

import org.booklore.model.enums.AuthorMetadataSource

data class AuthorMatchRequest(
    val source: AuthorMetadataSource? = null,
    val asin: String? = null,
    val region: String? = "us"
)

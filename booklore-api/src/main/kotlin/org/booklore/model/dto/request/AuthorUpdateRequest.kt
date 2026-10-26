package org.booklore.model.dto.request

data class AuthorUpdateRequest(
    val name: String? = null,
    val description: String? = null,
    val asin: String? = null,
    val nameLocked: Boolean? = null,
    val descriptionLocked: Boolean? = null,
    val asinLocked: Boolean? = null,
    val photoLocked: Boolean? = null
)

package org.booklore.model.dto.request

data class ChangeUserPasswordRequest(
    val userId: Long? = null,
    val newPassword: String? = null
)

package org.booklore.model.dto.request

data class ChangePasswordRequest(
    val currentPassword: String? = null,
    val newPassword: String? = null
)

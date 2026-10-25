package org.booklore.model.dto.request

data class UpdateUserSettingRequest(
    val key: String? = null,
    val value: Any? = null
)

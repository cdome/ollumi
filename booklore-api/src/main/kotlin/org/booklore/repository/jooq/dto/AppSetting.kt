package org.booklore.repository.jooq.dto

data class AppSetting(
    val id: Long,
    val name: String,
    val value: String?,
)

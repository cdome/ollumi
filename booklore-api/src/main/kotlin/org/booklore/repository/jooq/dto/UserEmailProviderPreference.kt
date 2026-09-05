package org.booklore.repository.jooq.dto

data class UserEmailProviderPreference(
    val id: Long,
    val userId: Long,
    val defaultProviderId: Long,
)

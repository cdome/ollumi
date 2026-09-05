package org.booklore.repository.jooq.dto

/** Domain view of a `kobo_user_settings` row (replaces KoboUserSettingsEntity). */
data class KoboUserSettings(
    val id: Long,
    val userId: Long,
    val token: String?,
    val syncEnabled: Boolean,
    val progressMarkAsReadingThreshold: Float?,
    val progressMarkAsFinishedThreshold: Float?,
    val autoAddToShelf: Boolean,
    val hardcoverApiKey: String?,
    val hardcoverSyncEnabled: Boolean,
    val twoWayProgressSync: Boolean,
)

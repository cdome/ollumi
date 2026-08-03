package org.booklore.repository.jooq.dto

/**
 * A single persisted user setting (one row of user_settings), replacing the JPA
 * UserSettingEntity. Consumers only read the key + (JSON-or-scalar) value string and
 * write via the repository's upsert, so this is a minimal immutable projection.
 */
data class UserSettingRow(
    val settingKey: String,
    val settingValue: String,
)

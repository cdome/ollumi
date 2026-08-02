package org.booklore.repository.jooq.dto

import java.time.Instant

/**
 * Mutable carrier for koreader_user rows. KoreaderUserService upserts via find-or-new -> set -> save,
 * mirroring the old managed-entity shape (no Hibernate/session).
 */
data class KoreaderUserRow @JvmOverloads constructor(
    var id: Long? = null,
    var username: String? = null,
    var password: String? = null,
    var passwordMD5: String? = null,
    var syncEnabled: Boolean = false,
    var syncWithBookloreReader: Boolean = false,
    var bookLoreUserId: Long? = null,
    var createdAt: Instant? = null,
    var updatedAt: Instant? = null,
)

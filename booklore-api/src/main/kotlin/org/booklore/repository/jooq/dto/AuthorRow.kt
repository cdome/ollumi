package org.booklore.repository.jooq.dto

/**
 * Mutable domain row replacing the JPA AuthorEntity author-profile columns (replaces
 * AuthorMetadataService's find-or-new -> set-a-subset -> save() write style). Unlike mood/category/tag
 * masters, an author is a first-class profile row (description/asin/lock flags), not just a name.
 */
data class AuthorRow @JvmOverloads constructor(
    var id: Long? = null,
    var name: String? = null,
    var description: String? = null,
    var asin: String? = null,
    var nameLocked: Boolean = false,
    var descriptionLocked: Boolean = false,
    var asinLocked: Boolean = false,
    var photoLocked: Boolean = false,
)

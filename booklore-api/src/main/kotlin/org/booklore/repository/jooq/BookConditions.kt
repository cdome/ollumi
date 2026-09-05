package org.booklore.repository.jooq

import org.booklore.jooq.tables.Book.BOOK
import org.jooq.Condition

object BookConditions {

    @JvmStatic
    fun notDeleted(): Condition =
        BOOK.DELETED.isNull.or(BOOK.DELETED.eq(0))
}

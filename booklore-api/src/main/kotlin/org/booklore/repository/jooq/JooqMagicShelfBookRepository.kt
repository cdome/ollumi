package org.booklore.repository.jooq

import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookFile.BOOK_FILE
import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.jooq.tables.UserBookProgress.USER_BOOK_PROGRESS
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.SelectOnConditionStep
import org.jooq.SelectSelectStep
import org.jooq.impl.DSL.countDistinct
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

/**
 * Driving query for Magic Shelf rule conditions produced by [JooqBookRuleEvaluator]:
 * BOOK left-joined with metadata, all users' progress rows and book files
 * (the evaluator's top-level user condition filters the progress rows).
 */
@Repository
class JooqMagicShelfBookRepository(private val dsl: DSLContext) {

    fun findBookIds(condition: Condition, pageable: Pageable): Page<Long> {
        val total = withRuleJoins(dsl.select(countDistinct(BOOK.ID)))
            .where(condition)
            .fetchOne(0, Long::class.java)!!

        val ids = withRuleJoins(dsl.selectDistinct(BOOK.ID))
            .where(condition)
            .orderBy(BOOK.ID)
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch(BOOK.ID)

        return PageableHelper.toPage(ids, total, pageable)
    }

    private fun <R : Record> withRuleJoins(select: SelectSelectStep<R>): SelectOnConditionStep<R> =
        select.from(BOOK)
            .leftJoin(BOOK_METADATA).on(BOOK_METADATA.BOOK_ID.eq(BOOK.ID))
            .leftJoin(USER_BOOK_PROGRESS).on(USER_BOOK_PROGRESS.BOOK_ID.eq(BOOK.ID))
            .leftJoin(BOOK_FILE).on(BOOK_FILE.BOOK_ID.eq(BOOK.ID))
}

package org.booklore.repository.jooq

import org.booklore.jooq.tables.MetadataFetchJobs.METADATA_FETCH_JOBS
import org.booklore.jooq.tables.MetadataFetchProposals.METADATA_FETCH_PROPOSALS
import org.booklore.jooq.tables.records.MetadataFetchJobsRecord
import org.booklore.jooq.tables.records.MetadataFetchProposalsRecord
import org.booklore.model.enums.FetchedMetadataProposalStatus
import org.booklore.model.enums.MetadataFetchTaskStatus
import org.booklore.repository.jooq.dto.MetadataFetchJobRow
import org.booklore.repository.jooq.dto.MetadataFetchProposalRow
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional

@Repository
class JooqMetadataFetchJobRepository(private val dsl: DSLContext) {

    private val j = METADATA_FETCH_JOBS
    private val p = METADATA_FETCH_PROPOSALS

    // ---------- reads ----------

    fun findById(taskId: String): Optional<MetadataFetchJobRow> {
        val job = dsl.selectFrom(j).where(j.TASK_ID.eq(taskId)).fetchOne() ?: return Optional.empty()
        val proposals = dsl.selectFrom(p).where(p.TASK_ID.eq(taskId)).fetch().map(::toProposalRow)
        return Optional.of(toJobRow(job, proposals))
    }

    fun findAllWithProposals(): List<MetadataFetchJobRow> {
        val jobs = dsl.selectFrom(j).fetch()
        if (jobs.isEmpty()) return emptyList()
        val proposalsByTask = dsl.selectFrom(p).fetch().map(::toProposalRow).groupBy { it.taskId }
        return jobs.map { toJobRow(it, proposalsByTask[it.taskId] ?: emptyList()) }
    }

    fun countAll(): Long = dsl.fetchCount(j).toLong()

    fun findProposalById(proposalId: Long): Optional<MetadataFetchProposalRow> =
        Optional.ofNullable(dsl.selectFrom(p).where(p.PROPOSAL_ID.eq(proposalId)).fetchOne()?.let(::toProposalRow))

    // ---------- job writes ----------

    fun insertJob(
        taskId: String,
        userId: Long?,
        status: MetadataFetchTaskStatus,
        startedAt: Instant,
        totalBooksCount: Int?,
        completedBooks: Int?,
    ) {
        dsl.insertInto(j)
            .set(j.TASK_ID, taskId)
            .set(j.USER_ID, userId)
            .set(j.STATUS, status.name)
            .set(j.STARTED_AT, startedAt.toLdt())
            .set(j.TOTAL_BOOKS_COUNT, totalBooksCount)
            .set(j.COMPLETED_BOOKS, completedBooks)
            .execute()
    }

    fun updateCompletedBooks(taskId: String, completedBooks: Int) {
        dsl.update(j).set(j.COMPLETED_BOOKS, completedBooks).where(j.TASK_ID.eq(taskId)).execute()
    }

    fun markCompleted(taskId: String, completedBooks: Int, completedAt: Instant) {
        dsl.update(j)
            .set(j.STATUS, MetadataFetchTaskStatus.COMPLETED.name)
            .set(j.COMPLETED_AT, completedAt.toLdt())
            .set(j.COMPLETED_BOOKS, completedBooks)
            .where(j.TASK_ID.eq(taskId))
            .execute()
    }

    fun markCancelled(taskId: String, completedAt: Instant) {
        dsl.update(j)
            .set(j.STATUS, MetadataFetchTaskStatus.CANCELLED.name)
            .set(j.COMPLETED_AT, completedAt.toLdt())
            .where(j.TASK_ID.eq(taskId))
            .execute()
    }

    /** Deletes the job; DB `ON DELETE CASCADE` removes its proposals. Returns whether a row existed. */
    fun deleteById(taskId: String): Boolean =
        dsl.deleteFrom(j).where(j.TASK_ID.eq(taskId)).execute() > 0

    fun deleteAllByCompletedAtBefore(cutoff: Instant): Int =
        dsl.deleteFrom(j).where(j.COMPLETED_AT.lt(cutoff.toLdt())).execute()

    fun deleteAllRecords(): Int = dsl.deleteFrom(j).execute()

    // ---------- proposal writes ----------

    fun insertProposal(
        taskId: String,
        bookId: Long,
        metadataJson: String,
        status: FetchedMetadataProposalStatus,
        fetchedAt: Instant,
    ): Long =
        dsl.insertInto(p)
            .set(p.TASK_ID, taskId)
            .set(p.BOOK_ID, bookId)
            .set(p.METADATA_JSON, metadataJson)
            .set(p.STATUS, status.name)
            .set(p.FETCHED_AT, fetchedAt.toLdt())
            .returning(p.PROPOSAL_ID)
            .fetchOne()!!.proposalId

    fun updateProposalReview(
        proposalId: Long,
        status: FetchedMetadataProposalStatus,
        reviewedAt: Instant,
        reviewerUserId: Long,
    ) {
        dsl.update(p)
            .set(p.STATUS, status.name)
            .set(p.REVIEWED_AT, reviewedAt.toLdt())
            .set(p.REVIEWER_USER_ID, reviewerUserId)
            .where(p.PROPOSAL_ID.eq(proposalId))
            .execute()
    }

    // ---------- mapping ----------

    private fun toJobRow(r: MetadataFetchJobsRecord, proposals: List<MetadataFetchProposalRow>) = MetadataFetchJobRow(
        taskId = r.taskId,
        userId = r.userId,
        status = MetadataFetchTaskStatus.valueOf(r.status),
        statusMessage = r.statusMessage,
        startedAt = r.startedAt.toInstant(),
        completedAt = r.completedAt?.toInstant(),
        totalBooksCount = r.totalBooksCount,
        completedBooks = r.completedBooks,
        proposals = proposals,
    )

    private fun toProposalRow(r: MetadataFetchProposalsRecord) = MetadataFetchProposalRow(
        proposalId = r.proposalId,
        taskId = r.taskId,
        bookId = r.bookId,
        fetchedAt = r.fetchedAt?.toInstant(),
        reviewedAt = r.reviewedAt?.toInstant(),
        reviewerUserId = r.reviewerUserId,
        status = FetchedMetadataProposalStatus.valueOf(r.status),
        metadataJson = r.metadataJson,
    )

    private fun Instant.toLdt(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneOffset.UTC)
    private fun LocalDateTime.toInstant(): Instant = this.toInstant(ZoneOffset.UTC)
}

package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.MetadataFetchJobs.METADATA_FETCH_JOBS
import org.booklore.jooq.tables.MetadataFetchProposals.METADATA_FETCH_PROPOSALS
import org.booklore.model.enums.FetchedMetadataProposalStatus
import org.booklore.model.enums.MetadataFetchTaskStatus
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.temporal.ChronoUnit

class JooqMetadataFetchJobRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqMetadataFetchJobRepository
    @Autowired private lateinit var dsl: DSLContext

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(METADATA_FETCH_PROPOSALS).execute()
        dsl.deleteFrom(METADATA_FETCH_JOBS).execute()
    }

    @Test
    fun `insertJob then findById returns the job with no proposals`() {
        val started = Instant.now()
        repository.insertJob("t1", 7L, MetadataFetchTaskStatus.IN_PROGRESS, started, 5, 0)

        val found = repository.findById("t1")
        assertThat(found).isPresent
        val job = found.get()
        assertThat(job.taskId).isEqualTo("t1")
        assertThat(job.userId).isEqualTo(7L)
        assertThat(job.status).isEqualTo(MetadataFetchTaskStatus.IN_PROGRESS)
        assertThat(job.totalBooksCount).isEqualTo(5)
        assertThat(job.completedBooks).isEqualTo(0)
        assertThat(job.completedAt).isNull()
        assertThat(job.startedAt).isNotNull()
        assertThat(job.proposals).isEmpty()
    }

    @Test
    fun `insertJob accepts a null userId`() {
        repository.insertJob("t-null", null, MetadataFetchTaskStatus.IN_PROGRESS, Instant.now(), 1, 0)
        assertThat(repository.findById("t-null").get().userId).isNull()
    }

    @Test
    fun `findById is empty for unknown taskId`() {
        assertThat(repository.findById("missing")).isEmpty
    }

    @Test
    fun `insertProposal is returned inside the job aggregate`() {
        repository.insertJob("t1", 1L, MetadataFetchTaskStatus.IN_PROGRESS, Instant.now(), 2, 0)
        val id1 = repository.insertProposal("t1", 100L, """{"title":"A"}""", FetchedMetadataProposalStatus.FETCHED, Instant.now())
        repository.insertProposal("t1", 200L, """{"title":"B"}""", FetchedMetadataProposalStatus.FETCHED, Instant.now())

        val job = repository.findById("t1").get()
        assertThat(job.proposals).hasSize(2)
        assertThat(job.proposals.map { it.bookId }).containsExactlyInAnyOrder(100L, 200L)
        val p1 = job.proposals.first { it.proposalId == id1 }
        assertThat(p1.taskId).isEqualTo("t1")
        assertThat(p1.status).isEqualTo(FetchedMetadataProposalStatus.FETCHED)
        assertThat(p1.metadataJson).isEqualTo("""{"title":"A"}""")
        assertThat(p1.fetchedAt).isNotNull()
        assertThat(p1.reviewedAt).isNull()
        assertThat(p1.reviewerUserId).isNull()
    }

    @Test
    fun `updateCompletedBooks updates the counter`() {
        repository.insertJob("t1", 1L, MetadataFetchTaskStatus.IN_PROGRESS, Instant.now(), 5, 0)
        repository.updateCompletedBooks("t1", 3)
        assertThat(repository.findById("t1").get().completedBooks).isEqualTo(3)
    }

    @Test
    fun `markCompleted sets status completedBooks and completedAt`() {
        repository.insertJob("t1", 1L, MetadataFetchTaskStatus.IN_PROGRESS, Instant.now(), 5, 0)
        val completedAt = Instant.now()
        repository.markCompleted("t1", 5, completedAt)

        val job = repository.findById("t1").get()
        assertThat(job.status).isEqualTo(MetadataFetchTaskStatus.COMPLETED)
        assertThat(job.completedBooks).isEqualTo(5)
        assertThat(job.completedAt).isNotNull()
    }

    @Test
    fun `markCancelled sets status and completedAt`() {
        repository.insertJob("t1", 1L, MetadataFetchTaskStatus.IN_PROGRESS, Instant.now(), 5, 2)
        repository.markCancelled("t1", Instant.now())

        val job = repository.findById("t1").get()
        assertThat(job.status).isEqualTo(MetadataFetchTaskStatus.CANCELLED)
        assertThat(job.completedAt).isNotNull()
        assertThat(job.completedBooks).isEqualTo(2) // unchanged
    }

    @Test
    fun `findProposalById returns the proposal`() {
        repository.insertJob("t1", 1L, MetadataFetchTaskStatus.IN_PROGRESS, Instant.now(), 1, 0)
        val id = repository.insertProposal("t1", 100L, """{"x":1}""", FetchedMetadataProposalStatus.FETCHED, Instant.now())

        val found = repository.findProposalById(id)
        assertThat(found).isPresent
        assertThat(found.get().bookId).isEqualTo(100L)
        assertThat(repository.findProposalById(999_999L)).isEmpty
    }

    @Test
    fun `updateProposalReview sets status reviewedAt and reviewer`() {
        repository.insertJob("t1", 1L, MetadataFetchTaskStatus.IN_PROGRESS, Instant.now(), 1, 0)
        val id = repository.insertProposal("t1", 100L, """{"x":1}""", FetchedMetadataProposalStatus.FETCHED, Instant.now())

        repository.updateProposalReview(id, FetchedMetadataProposalStatus.ACCEPTED, Instant.now(), 42L)

        val p = repository.findProposalById(id).get()
        assertThat(p.status).isEqualTo(FetchedMetadataProposalStatus.ACCEPTED)
        assertThat(p.reviewedAt).isNotNull()
        assertThat(p.reviewerUserId).isEqualTo(42L)
    }

    @Test
    fun `deleteById returns true and cascade-deletes proposals`() {
        repository.insertJob("t1", 1L, MetadataFetchTaskStatus.IN_PROGRESS, Instant.now(), 1, 0)
        repository.insertProposal("t1", 100L, """{"x":1}""", FetchedMetadataProposalStatus.FETCHED, Instant.now())

        assertThat(repository.deleteById("t1")).isTrue()
        assertThat(repository.findById("t1")).isEmpty
        assertThat(dsl.fetchCount(METADATA_FETCH_PROPOSALS)).isZero()
        assertThat(repository.deleteById("nope")).isFalse()
    }

    @Test
    fun `deleteAllByCompletedAtBefore removes only old completed jobs`() {
        repository.insertJob("old", 1L, MetadataFetchTaskStatus.IN_PROGRESS, Instant.now(), 1, 0)
        repository.insertJob("new", 1L, MetadataFetchTaskStatus.IN_PROGRESS, Instant.now(), 1, 0)
        repository.markCompleted("old", 1, Instant.now().minus(10, ChronoUnit.DAYS))
        repository.markCompleted("new", 1, Instant.now())

        val deleted = repository.deleteAllByCompletedAtBefore(Instant.now().minus(5, ChronoUnit.DAYS))
        assertThat(deleted).isEqualTo(1)
        assertThat(repository.findById("old")).isEmpty
        assertThat(repository.findById("new")).isPresent
    }

    @Test
    fun `deleteAllRecords and countAll`() {
        repository.insertJob("a", 1L, MetadataFetchTaskStatus.IN_PROGRESS, Instant.now(), 1, 0)
        repository.insertJob("b", 1L, MetadataFetchTaskStatus.IN_PROGRESS, Instant.now(), 1, 0)
        assertThat(repository.countAll()).isEqualTo(2)

        val deleted = repository.deleteAllRecords()
        assertThat(deleted).isEqualTo(2)
        assertThat(repository.countAll()).isZero()
    }

    @Test
    fun `findAllWithProposals groups proposals under their job`() {
        repository.insertJob("j1", 1L, MetadataFetchTaskStatus.COMPLETED, Instant.now(), 2, 2)
        repository.insertJob("j2", 1L, MetadataFetchTaskStatus.IN_PROGRESS, Instant.now(), 1, 0)
        repository.insertProposal("j1", 10L, """{"a":1}""", FetchedMetadataProposalStatus.FETCHED, Instant.now())
        repository.insertProposal("j1", 11L, """{"b":2}""", FetchedMetadataProposalStatus.ACCEPTED, Instant.now())

        val all = repository.findAllWithProposals()
        assertThat(all).hasSize(2)
        assertThat(all.first { it.taskId == "j1" }.proposals).hasSize(2)
        assertThat(all.first { it.taskId == "j2" }.proposals).isEmpty()
    }
}

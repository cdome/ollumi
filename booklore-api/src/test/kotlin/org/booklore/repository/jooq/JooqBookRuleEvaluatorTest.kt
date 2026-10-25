package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.Author.AUTHOR
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.BookFile.BOOK_FILE
import org.booklore.jooq.tables.BookMetadata.BOOK_METADATA
import org.booklore.jooq.tables.BookMetadataAuthorMapping.BOOK_METADATA_AUTHOR_MAPPING
import org.booklore.jooq.tables.BookMetadataCategoryMapping.BOOK_METADATA_CATEGORY_MAPPING
import org.booklore.jooq.tables.BookMetadataMoodMapping.BOOK_METADATA_MOOD_MAPPING
import org.booklore.jooq.tables.BookMetadataTagMapping.BOOK_METADATA_TAG_MAPPING
import org.booklore.jooq.tables.BookShelfMapping.BOOK_SHELF_MAPPING
import org.booklore.jooq.tables.Category.CATEGORY
import org.booklore.jooq.tables.ComicCreator.COMIC_CREATOR
import org.booklore.jooq.tables.ComicMetadata.COMIC_METADATA
import org.booklore.jooq.tables.ComicMetadataCreatorMapping.COMIC_METADATA_CREATOR_MAPPING
import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.Mood.MOOD
import org.booklore.jooq.tables.Shelf.SHELF
import org.booklore.jooq.tables.Tag.TAG
import org.booklore.jooq.tables.UserBookProgress.USER_BOOK_PROGRESS
import org.booklore.jooq.tables.Users.USERS
import org.booklore.model.dto.GroupRule
import org.booklore.model.dto.JoinType
import org.booklore.model.dto.Rule
import org.booklore.model.dto.RuleField
import org.booklore.model.dto.RuleOperator
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import java.time.LocalDate
import java.time.LocalDateTime

class JooqBookRuleEvaluatorTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var evaluator: JooqBookRuleEvaluator

    @Autowired
    private lateinit var repository: JooqMagicShelfBookRepository

    @Autowired
    private lateinit var dsl: DSLContext

    private var user1Id = 0L
    private var user2Id = 0L
    private var lib1Id = 0L
    private var lib2Id = 0L
    private var shelfAId = 0L

    private var hobbitId = 0L   // READ+rated by user1, author/category/tags/moods/shelf, epub 100%
    private var duneId = 0L     // READING by user1, physical, kobo 42%
    private var gatsbyId = 0L   // no progress, no relations, sparse metadata, pdf
    private var audioId = 0L    // audiobook file, narrator, abridged, no progress
    private var comicId = 0L    // lib2, comic creator (penciller)
    private var quirkId = 0L    // progress row for user2 only — JPA visibility quirk

    // Series fixtures
    private var a1Id = 0L; private var a2Id = 0L; private var a3Id = 0L      // Alpha #1 READ, #2 READING, #3 —
    private var b1Id = 0L; private var b2Id = 0L                              // Beta #1+#2 READ, total 2
    private var g1Id = 0L                                                     // Gamma #1, total 3
    private var d2Id = 0L; private var d3Id = 0L                              // Delta #2, #3 (no #1)
    private var e1Id = 0L; private var e1bId = 0L                             // Echo #1, #1 duplicate

    @BeforeEach
    fun setUp() {
        listOf(
            USER_BOOK_PROGRESS, BOOK_SHELF_MAPPING, SHELF,
            BOOK_METADATA_AUTHOR_MAPPING, AUTHOR,
            BOOK_METADATA_CATEGORY_MAPPING, CATEGORY,
            BOOK_METADATA_MOOD_MAPPING, MOOD,
            BOOK_METADATA_TAG_MAPPING, TAG,
            COMIC_METADATA_CREATOR_MAPPING, COMIC_CREATOR, COMIC_METADATA,
            BOOK_FILE, BOOK_METADATA, BOOK, LIBRARY, USERS
        ).forEach { dsl.deleteFrom(it).execute() }

        user1Id = insertUser("alice")
        user2Id = insertUser("bob")
        lib1Id = insertLibrary("Main")
        lib2Id = insertLibrary("Comics")

        val now = LocalDateTime.now()

        hobbitId = insertBook(lib1Id, addedOn = now.minusDays(10), coverHash = "hash-1", isPhysical = false)
        insertMetadata(
            hobbitId, title = "The Hobbit", publisher = "Allen & Unwin", language = "en",
            pageCount = 310, publishedDate = LocalDate.of(1937, 9, 21), goodreadsRating = 4.3
        )
        insertFile(hobbitId, "hobbit.epub", "EPUB", sizeKb = 1000)
        insertProgress(user1Id, hobbitId, readStatus = "READ", rating = 5, epubPercent = 100.0)

        duneId = insertBook(lib1Id, addedOn = now.minusDays(100), isPhysical = true)
        insertMetadata(duneId, title = "Dune", publisher = "Chilton", language = "en", pageCount = 412)
        insertFile(duneId, "dune.epub", "EPUB", sizeKb = 2000)
        insertProgress(user1Id, duneId, readStatus = "READING", koboPercent = 42.0)

        gatsbyId = insertBook(lib1Id, addedOn = now.minusDays(1))
        insertMetadata(
            gatsbyId, title = "The Great Gatsby", language = "fr", pageCount = 180,
            publishedDate = LocalDate.now()
        )
        insertFile(gatsbyId, "gatsby.pdf", "PDF", sizeKb = 500)

        audioId = insertBook(lib1Id, addedOn = now.minusDays(200))
        insertMetadata(audioId, title = "Project Hail Mary", narrator = "Ray Porter", abridged = true)
        insertFile(
            audioId, "phm.m4b", "AUDIOBOOK", sizeKb = 90_000,
            durationSeconds = 57_600, codec = "aac", bitrate = 128, chapterCount = 32
        )

        comicId = insertBook(lib2Id, addedOn = now.minusDays(200))
        insertMetadata(comicId, title = "Saga Vol 1")
        insertFile(comicId, "saga.cbz", "CBX", sizeKb = 300)
        dsl.insertInto(COMIC_METADATA).set(COMIC_METADATA.BOOK_ID, comicId).execute()
        val creatorId = dsl.insertInto(COMIC_CREATOR).set(COMIC_CREATOR.NAME, "Fiona Staples")
            .returningResult(COMIC_CREATOR.ID).fetchOne()!!.get(COMIC_CREATOR.ID)!!
        dsl.insertInto(COMIC_METADATA_CREATOR_MAPPING)
            .set(COMIC_METADATA_CREATOR_MAPPING.BOOK_ID, comicId)
            .set(COMIC_METADATA_CREATOR_MAPPING.CREATOR_ID, creatorId)
            .set(COMIC_METADATA_CREATOR_MAPPING.ROLE, "PENCILLER")
            .execute()

        quirkId = insertBook(lib1Id, addedOn = now.minusDays(200))
        insertMetadata(quirkId, title = "Quirk Book")
        insertProgress(user2Id, quirkId, readStatus = "READ")

        // Relations for hobbit
        linkAuthor(hobbitId, "J.R.R. Tolkien")
        linkCategory(hobbitId, "Fantasy")
        linkCategory(duneId, "SciFi")
        linkMood(hobbitId, "Cozy")
        linkTag(hobbitId, "Classic")
        linkTag(hobbitId, "Epic")
        shelfAId = dsl.insertInto(SHELF).set(SHELF.USER_ID, user1Id).set(SHELF.NAME, "Favorites")
            .returningResult(SHELF.ID).fetchOne()!!.get(SHELF.ID)!!
        dsl.insertInto(BOOK_SHELF_MAPPING)
            .set(BOOK_SHELF_MAPPING.BOOK_ID, hobbitId).set(BOOK_SHELF_MAPPING.SHELF_ID, shelfAId).execute()

        // Series
        a1Id = seriesBook("Alpha", 1.0); a2Id = seriesBook("Alpha", 2.0); a3Id = seriesBook("Alpha", 3.0)
        insertProgress(user1Id, a1Id, readStatus = "READ")
        insertProgress(user1Id, a2Id, readStatus = "READING")

        b1Id = seriesBook("Beta", 1.0, total = 2); b2Id = seriesBook("Beta", 2.0, total = 2)
        insertProgress(user1Id, b1Id, readStatus = "READ")
        insertProgress(user1Id, b2Id, readStatus = "READ")

        g1Id = seriesBook("Gamma", 1.0, total = 3)
        d2Id = seriesBook("Delta", 2.0); d3Id = seriesBook("Delta", 3.0)
        e1Id = seriesBook("Echo", 1.0); e1bId = seriesBook("Echo", 1.0)
    }

    // =============================================================
    // Helpers
    // =============================================================

    private fun rule(
        field: RuleField, op: RuleOperator, value: Any? = null,
        valueStart: Any? = null, valueEnd: Any? = null
    ) = Rule(null, field, op, value, valueStart, valueEnd)

    private fun group(vararg rules: Any, join: JoinType = JoinType.AND) =
        GroupRule(null, "group", join, rules.toList())

    private fun ids(group: GroupRule, userId: Long? = user1Id): List<Long> =
        repository.findBookIds(evaluator.toCondition(group, userId), PageRequest.of(0, 500)).content

    private fun ids(vararg rules: Any): List<Long> = ids(group(*rules))

    // =============================================================
    // Groups, EQUALS / NOT_EQUALS
    // =============================================================

    @Test
    fun `equals matches title case-insensitively`() {
        assertThat(ids(rule(RuleField.TITLE, RuleOperator.EQUALS, "the hobbit")))
            .containsExactly(hobbitId)
    }

    @Test
    fun `or group combines rules`() {
        val g = group(
            rule(RuleField.TITLE, RuleOperator.EQUALS, "The Hobbit"),
            rule(RuleField.TITLE, RuleOperator.EQUALS, "Dune"),
            join = JoinType.OR
        )
        assertThat(ids(g)).containsExactlyInAnyOrder(hobbitId, duneId)
    }

    @Test
    fun `nested groups compose`() {
        val g = group(
            rule(RuleField.LANGUAGE, RuleOperator.EQUALS, "en"),
            group(
                rule(RuleField.TITLE, RuleOperator.EQUALS, "The Hobbit"),
                rule(RuleField.TITLE, RuleOperator.EQUALS, "The Great Gatsby"),
                join = JoinType.OR
            )
        )
        assertThat(ids(g)).containsExactly(hobbitId)
    }

    @Test
    fun `empty group matches all visible books`() {
        val all = ids(group())
        assertThat(all).contains(hobbitId, duneId, gatsbyId, audioId, comicId, a1Id)
        // no duplicates despite joined files/progress rows
        assertThat(all).doesNotHaveDuplicates()
    }

    @Test
    fun `not equals on language uses SQL null semantics`() {
        // books without a language value are excluded, like in JPA
        assertThat(ids(rule(RuleField.LANGUAGE, RuleOperator.NOT_EQUALS, "en")))
            .containsExactly(gatsbyId)
    }

    @Test
    fun `equals on booleans`() {
        assertThat(
            ids(
                rule(RuleField.TITLE, RuleOperator.CONTAINS, "dune"),
                rule(RuleField.IS_PHYSICAL, RuleOperator.EQUALS, "true")
            )
        ).containsExactly(duneId)
        assertThat(
            ids(
                rule(RuleField.TITLE, RuleOperator.CONTAINS, "hobbit"),
                rule(RuleField.IS_PHYSICAL, RuleOperator.EQUALS, "true")
            )
        ).isEmpty()
        assertThat(ids(rule(RuleField.ABRIDGED, RuleOperator.EQUALS, "true")))
            .containsExactly(audioId)
    }

    @Test
    fun `equals on published date`() {
        assertThat(ids(rule(RuleField.PUBLISHED_DATE, RuleOperator.EQUALS, "1937-09-21")))
            .containsExactly(hobbitId)
    }

    @Test
    fun `equals on library`() {
        assertThat(ids(rule(RuleField.LIBRARY, RuleOperator.EQUALS, lib2Id)))
            .containsExactly(comicId)
    }

    // =============================================================
    // READ_STATUS semantics
    // =============================================================

    @Test
    fun `read status equals`() {
        assertThat(ids(rule(RuleField.READ_STATUS, RuleOperator.EQUALS, "READING")))
            .containsExactly(duneId, a2Id)
    }

    @Test
    fun `read status UNSET matches books without progress`() {
        val g = group(
            rule(RuleField.TITLE, RuleOperator.CONTAINS, "gatsby"),
            rule(RuleField.READ_STATUS, RuleOperator.EQUALS, "UNSET")
        )
        assertThat(ids(g)).containsExactly(gatsbyId)
    }

    @Test
    fun `read status not equals includes books without progress`() {
        val g = group(
            rule(RuleField.TITLE, RuleOperator.STARTS_WITH, "the great"),
            rule(RuleField.READ_STATUS, RuleOperator.NOT_EQUALS, "READ")
        )
        assertThat(ids(g)).containsExactly(gatsbyId)
    }

    @Test
    fun `read status includes any with UNSET`() {
        val g = group(
            rule(RuleField.TITLE, RuleOperator.STARTS_WITH, "the"),
            rule(RuleField.READ_STATUS, RuleOperator.INCLUDES_ANY, listOf("READING", "UNSET"))
        )
        // gatsby (no progress) matches UNSET; hobbit is READ and drops out
        assertThat(ids(g)).containsExactly(gatsbyId)
    }

    @Test
    fun `read status excludes all keeps unset books`() {
        val g = group(
            rule(RuleField.TITLE, RuleOperator.CONTAINS, "gatsby"),
            rule(RuleField.READ_STATUS, RuleOperator.EXCLUDES_ALL, listOf("READ"))
        )
        assertThat(ids(g)).containsExactly(gatsbyId)
        val g2 = group(
            rule(RuleField.TITLE, RuleOperator.CONTAINS, "hobbit"),
            rule(RuleField.READ_STATUS, RuleOperator.EXCLUDES_ALL, listOf("READ"))
        )
        assertThat(ids(g2)).isEmpty()
    }

    @Test
    fun `books with only another user's progress are invisible - JPA parity quirk`() {
        val g = group(rule(RuleField.TITLE, RuleOperator.CONTAINS, "quirk"))
        assertThat(ids(g, userId = user1Id)).isEmpty()
        assertThat(ids(g, userId = user2Id)).containsExactly(quirkId)
    }

    // =============================================================
    // String operators
    // =============================================================

    @Test
    fun `contains, starts with, ends with`() {
        assertThat(ids(rule(RuleField.TITLE, RuleOperator.CONTAINS, "great")))
            .containsExactly(gatsbyId)
        assertThat(ids(rule(RuleField.TITLE, RuleOperator.STARTS_WITH, "the h")))
            .containsExactly(hobbitId)
        assertThat(ids(rule(RuleField.TITLE, RuleOperator.ENDS_WITH, "mary")))
            .containsExactly(audioId)
    }

    @Test
    fun `does not contain excludes matches`() {
        val g = group(
            rule(RuleField.LANGUAGE, RuleOperator.EQUALS, "en"),
            rule(RuleField.TITLE, RuleOperator.DOES_NOT_CONTAIN, "hobbit")
        )
        assertThat(ids(g)).containsExactly(duneId)
    }

    @Test
    fun `contains matches array field via subquery`() {
        assertThat(ids(rule(RuleField.AUTHORS, RuleOperator.CONTAINS, "tolkien")))
            .containsExactly(hobbitId)
    }

    // =============================================================
    // Numeric and date comparisons
    // =============================================================

    @Test
    fun `numeric comparisons on page count`() {
        assertThat(ids(rule(RuleField.PAGE_COUNT, RuleOperator.GREATER_THAN, 300)))
            .containsExactlyInAnyOrder(hobbitId, duneId)
        assertThat(ids(rule(RuleField.PAGE_COUNT, RuleOperator.GREATER_THAN_EQUAL_TO, 412)))
            .containsExactly(duneId)
        assertThat(ids(rule(RuleField.PAGE_COUNT, RuleOperator.LESS_THAN, 200)))
            .containsExactly(gatsbyId)
        assertThat(ids(rule(RuleField.PAGE_COUNT, RuleOperator.LESS_THAN_EQUAL_TO, 180)))
            .containsExactly(gatsbyId)
    }

    @Test
    fun `fractional comparison on rating`() {
        assertThat(ids(rule(RuleField.GOODREADS_RATING, RuleOperator.GREATER_THAN, 4.0)))
            .containsExactly(hobbitId)
        assertThat(ids(rule(RuleField.GOODREADS_RATING, RuleOperator.GREATER_THAN, 4.5)))
            .isEmpty()
    }

    @Test
    fun `personal rating comparison`() {
        assertThat(ids(rule(RuleField.PERSONAL_RATING, RuleOperator.GREATER_THAN_EQUAL_TO, 5)))
            .containsExactly(hobbitId)
    }

    @Test
    fun `in between on numbers and dates`() {
        assertThat(ids(rule(RuleField.PAGE_COUNT, RuleOperator.IN_BETWEEN, valueStart = 100, valueEnd = 200)))
            .containsExactly(gatsbyId)
        assertThat(
            ids(rule(RuleField.PUBLISHED_DATE, RuleOperator.IN_BETWEEN, valueStart = "1930-01-01", valueEnd = "1940-01-01"))
        ).containsExactly(hobbitId)
    }

    // =============================================================
    // IS_EMPTY / IS_NOT_EMPTY
    // =============================================================

    @Test
    fun `is empty on string field`() {
        val g = group(
            rule(RuleField.TITLE, RuleOperator.CONTAINS, "gatsby"),
            rule(RuleField.PUBLISHER, RuleOperator.IS_EMPTY)
        )
        assertThat(ids(g)).containsExactly(gatsbyId)
        val g2 = group(
            rule(RuleField.TITLE, RuleOperator.CONTAINS, "hobbit"),
            rule(RuleField.PUBLISHER, RuleOperator.IS_NOT_EMPTY)
        )
        assertThat(ids(g2)).containsExactly(hobbitId)
    }

    @Test
    fun `is empty on array field`() {
        val g = group(
            rule(RuleField.TITLE, RuleOperator.CONTAINS, "gatsby"),
            rule(RuleField.AUTHORS, RuleOperator.IS_EMPTY)
        )
        assertThat(ids(g)).containsExactly(gatsbyId)
        val g2 = group(
            rule(RuleField.TITLE, RuleOperator.CONTAINS, "hobbit"),
            rule(RuleField.AUTHORS, RuleOperator.IS_EMPTY)
        )
        assertThat(ids(g2)).isEmpty()
    }

    @Test
    fun `shelf is empty and shelf equals by id`() {
        assertThat(ids(rule(RuleField.SHELF, RuleOperator.EQUALS, shelfAId.toString())))
            .containsExactly(hobbitId)
        val g = group(
            rule(RuleField.TITLE, RuleOperator.CONTAINS, "hobbit"),
            rule(RuleField.SHELF, RuleOperator.IS_EMPTY)
        )
        assertThat(ids(g)).isEmpty()
    }

    // =============================================================
    // Array include/exclude operators
    // =============================================================

    @Test
    fun `includes any on categories and genre alias`() {
        assertThat(ids(rule(RuleField.CATEGORIES, RuleOperator.INCLUDES_ANY, listOf("fantasy", "horror"))))
            .containsExactly(hobbitId)
        assertThat(ids(rule(RuleField.GENRE, RuleOperator.EQUALS, "scifi")))
            .containsExactly(duneId)
    }

    @Test
    fun `includes all on tags`() {
        assertThat(ids(rule(RuleField.TAGS, RuleOperator.INCLUDES_ALL, listOf("classic", "epic"))))
            .containsExactly(hobbitId)
        assertThat(ids(rule(RuleField.TAGS, RuleOperator.INCLUDES_ALL, listOf("classic", "horror"))))
            .isEmpty()
    }

    @Test
    fun `excludes all on categories`() {
        val g = group(
            rule(RuleField.LANGUAGE, RuleOperator.EQUALS, "en"),
            rule(RuleField.CATEGORIES, RuleOperator.EXCLUDES_ALL, listOf("scifi"))
        )
        assertThat(ids(g)).containsExactly(hobbitId)
    }

    @Test
    fun `includes any on moods`() {
        assertThat(ids(rule(RuleField.MOODS, RuleOperator.INCLUDES_ANY, listOf("cozy"))))
            .containsExactly(hobbitId)
    }

    // =============================================================
    // Relative dates
    // =============================================================

    @Test
    fun `within last days on addedOn`() {
        assertThat(ids(rule(RuleField.ADDED_ON, RuleOperator.WITHIN_LAST, 30, valueEnd = "days")))
            .containsExactlyInAnyOrder(hobbitId, gatsbyId)
    }

    @Test
    fun `older than days on addedOn`() {
        val g = group(
            rule(RuleField.TITLE, RuleOperator.CONTAINS, "dune"),
            rule(RuleField.ADDED_ON, RuleOperator.OLDER_THAN, 30, valueEnd = "days")
        )
        assertThat(ids(g)).containsExactly(duneId)
    }

    @Test
    fun `this period year on published date`() {
        assertThat(ids(rule(RuleField.PUBLISHED_DATE, RuleOperator.THIS_PERIOD, "year")))
            .containsExactly(gatsbyId)
    }

    // =============================================================
    // Progress, files, audiobook fields
    // =============================================================

    @Test
    fun `reading progress uses greatest of all sources`() {
        assertThat(ids(rule(RuleField.READING_PROGRESS, RuleOperator.GREATER_THAN, 50)))
            .containsExactly(hobbitId)
        assertThat(ids(rule(RuleField.READING_PROGRESS, RuleOperator.GREATER_THAN, 40)))
            .containsExactlyInAnyOrder(hobbitId, duneId)
    }

    @Test
    fun `file type matches extension`() {
        assertThat(ids(rule(RuleField.FILE_TYPE, RuleOperator.EQUALS, "pdf")))
            .containsExactly(gatsbyId)
        assertThat(ids(rule(RuleField.FILE_TYPE, RuleOperator.EQUALS, "epub")))
            .containsExactlyInAnyOrder(hobbitId, duneId)
    }

    @Test
    fun `file size comparison`() {
        assertThat(ids(rule(RuleField.FILE_SIZE, RuleOperator.GREATER_THAN, 1500)))
            .containsExactlyInAnyOrder(duneId, audioId)
    }

    @Test
    fun `audiobook fields`() {
        assertThat(ids(rule(RuleField.AUDIOBOOK_CODEC, RuleOperator.EQUALS, "aac")))
            .containsExactly(audioId)
        assertThat(ids(rule(RuleField.AUDIOBOOK_DURATION, RuleOperator.GREATER_THAN, 3600)))
            .containsExactly(audioId)
        assertThat(ids(rule(RuleField.AUDIOBOOK_BITRATE, RuleOperator.LESS_THAN, 200)))
            .containsExactly(audioId)
        assertThat(ids(rule(RuleField.AUDIOBOOK_CHAPTER_COUNT, RuleOperator.EQUALS, 32)))
            .containsExactly(audioId)
    }

    // =============================================================
    // METADATA_PRESENCE
    // =============================================================

    @Test
    fun `metadata presence on string and relation fields`() {
        val present = group(
            rule(RuleField.TITLE, RuleOperator.CONTAINS, "hobbit"),
            rule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "authors")
        )
        assertThat(ids(present)).containsExactly(hobbitId)

        val absent = group(
            rule(RuleField.TITLE, RuleOperator.CONTAINS, "gatsby"),
            rule(RuleField.METADATA_PRESENCE, RuleOperator.NOT_EQUALS, "publisher")
        )
        assertThat(ids(absent)).containsExactly(gatsbyId)
    }

    @Test
    fun `metadata presence on cover, personal rating, narrator and audiobook duration`() {
        assertThat(ids(rule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "thumbnailUrl")))
            .containsExactly(hobbitId)
        assertThat(ids(rule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "personalRating")))
            .containsExactly(hobbitId)
        assertThat(ids(rule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "narrator")))
            .containsExactly(audioId)
        assertThat(ids(rule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "audiobookDuration")))
            .containsExactly(audioId)
    }

    @Test
    fun `metadata presence on comic creator role`() {
        assertThat(ids(rule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "comicPencillers")))
            .containsExactly(comicId)
        assertThat(ids(rule(RuleField.METADATA_PRESENCE, RuleOperator.EQUALS, "comicInkers")))
            .isEmpty()
    }

    // =============================================================
    // Composite series fields
    // =============================================================

    @Test
    fun `series status reading`() {
        assertThat(ids(rule(RuleField.SERIES_STATUS, RuleOperator.EQUALS, "reading")))
            .containsExactlyInAnyOrder(a1Id, a2Id, a3Id)
    }

    @Test
    fun `series status not started`() {
        assertThat(ids(rule(RuleField.SERIES_STATUS, RuleOperator.EQUALS, "not_started")))
            .containsExactlyInAnyOrder(g1Id, d2Id, d3Id, e1Id, e1bId)
    }

    @Test
    fun `series status fully read`() {
        assertThat(ids(rule(RuleField.SERIES_STATUS, RuleOperator.EQUALS, "fully_read")))
            .containsExactlyInAnyOrder(b1Id, b2Id)
    }

    @Test
    fun `series status completed and ongoing`() {
        assertThat(ids(rule(RuleField.SERIES_STATUS, RuleOperator.EQUALS, "completed")))
            .containsExactlyInAnyOrder(b1Id, b2Id)
        assertThat(ids(rule(RuleField.SERIES_STATUS, RuleOperator.EQUALS, "ongoing")))
            .containsExactly(g1Id)
    }

    @Test
    fun `series status negation`() {
        val g = group(
            rule(RuleField.SERIES_NAME, RuleOperator.EQUALS, "beta"),
            rule(RuleField.SERIES_STATUS, RuleOperator.NOT_EQUALS, "reading")
        )
        assertThat(ids(g)).containsExactlyInAnyOrder(b1Id, b2Id)
    }

    @Test
    fun `series gaps`() {
        assertThat(ids(rule(RuleField.SERIES_GAPS, RuleOperator.EQUALS, "any_gap")))
            .containsExactlyInAnyOrder(d2Id, d3Id)
        assertThat(ids(rule(RuleField.SERIES_GAPS, RuleOperator.EQUALS, "missing_first")))
            .containsExactlyInAnyOrder(d2Id, d3Id)
        assertThat(ids(rule(RuleField.SERIES_GAPS, RuleOperator.EQUALS, "missing_latest")))
            .containsExactly(g1Id)
        assertThat(ids(rule(RuleField.SERIES_GAPS, RuleOperator.EQUALS, "duplicate_number")))
            .containsExactlyInAnyOrder(e1Id, e1bId)
    }

    @Test
    fun `series position first and last`() {
        val first = group(
            rule(RuleField.SERIES_NAME, RuleOperator.EQUALS, "alpha"),
            rule(RuleField.SERIES_POSITION, RuleOperator.EQUALS, "first_in_series")
        )
        assertThat(ids(first)).containsExactly(a1Id)

        val last = group(
            rule(RuleField.SERIES_NAME, RuleOperator.EQUALS, "alpha"),
            rule(RuleField.SERIES_POSITION, RuleOperator.EQUALS, "last_in_series")
        )
        assertThat(ids(last)).containsExactly(a3Id)
    }

    @Test
    fun `series position next unread`() {
        val g = group(
            rule(RuleField.SERIES_NAME, RuleOperator.EQUALS, "alpha"),
            rule(RuleField.SERIES_POSITION, RuleOperator.EQUALS, "next_unread")
        )
        assertThat(ids(g)).containsExactly(a2Id)
    }

    // =============================================================
    // Pagination via the driving repository
    // =============================================================

    @Test
    fun `pagination is distinct and stable`() {
        val condition = evaluator.toCondition(group(), user1Id)
        val page1 = repository.findBookIds(condition, PageRequest.of(0, 4))
        val page2 = repository.findBookIds(condition, PageRequest.of(1, 4))

        assertThat(page1.content).hasSize(4)
        assertThat(page1.content).doesNotContainAnyElementsOf(page2.content)
        assertThat(page1.totalElements).isEqualTo(page2.totalElements)
    }

    // =============================================================
    // Fixture helpers
    // =============================================================

    private fun insertUser(username: String): Long =
        dsl.insertInto(USERS)
            .set(USERS.USERNAME, username)
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte())
            .set(USERS.NAME, username)
            .returningResult(USERS.ID).fetchOne()!!.get(USERS.ID)!!

    private fun insertLibrary(name: String): Long =
        dsl.insertInto(LIBRARY).set(LIBRARY.NAME, name)
            .returningResult(LIBRARY.ID).fetchOne()!!.get(LIBRARY.ID)!!

    private fun insertBook(
        libraryId: Long,
        addedOn: LocalDateTime,
        coverHash: String? = null,
        isPhysical: Boolean? = false
    ): Long {
        val insert = dsl.insertInto(BOOK)
            .set(BOOK.LIBRARY_ID, libraryId)
            .set(BOOK.ADDED_ON, addedOn)
        if (coverHash != null) insert.set(BOOK.BOOK_COVER_HASH, coverHash)
        if (isPhysical != null) insert.set(BOOK.IS_PHYSICAL, if (isPhysical) 1.toByte() else 0.toByte())
        return insert.returningResult(BOOK.ID).fetchOne()!!.get(BOOK.ID)!!
    }

    private fun insertMetadata(
        bookId: Long,
        title: String? = null,
        publisher: String? = null,
        language: String? = null,
        pageCount: Int? = null,
        publishedDate: LocalDate? = null,
        goodreadsRating: Double? = null,
        narrator: String? = null,
        abridged: Boolean? = null,
        seriesName: String? = null,
        seriesNumber: Double? = null,
        seriesTotal: Int? = null
    ) {
        val insert = dsl.insertInto(BOOK_METADATA).set(BOOK_METADATA.BOOK_ID, bookId)
        if (title != null) insert.set(BOOK_METADATA.TITLE, title)
        if (publisher != null) insert.set(BOOK_METADATA.PUBLISHER, publisher)
        if (language != null) insert.set(BOOK_METADATA.LANGUAGE, language)
        if (pageCount != null) insert.set(BOOK_METADATA.PAGE_COUNT, pageCount)
        if (publishedDate != null) insert.set(BOOK_METADATA.PUBLISHED_DATE, publishedDate)
        if (goodreadsRating != null) insert.set(BOOK_METADATA.GOODREADS_RATING, goodreadsRating)
        if (narrator != null) insert.set(BOOK_METADATA.NARRATOR, narrator)
        if (abridged != null) insert.set(BOOK_METADATA.ABRIDGED, if (abridged) 1.toByte() else 0.toByte())
        if (seriesName != null) insert.set(BOOK_METADATA.SERIES_NAME, seriesName)
        if (seriesNumber != null) insert.set(BOOK_METADATA.SERIES_NUMBER, seriesNumber)
        if (seriesTotal != null) insert.set(BOOK_METADATA.SERIES_TOTAL, seriesTotal)
        insert.execute()
    }

    private fun insertFile(
        bookId: Long, fileName: String, bookType: String, sizeKb: Long? = null,
        durationSeconds: Long? = null, codec: String? = null, bitrate: Int? = null, chapterCount: Int? = null
    ) {
        val insert = dsl.insertInto(BOOK_FILE)
            .set(BOOK_FILE.BOOK_ID, bookId)
            .set(BOOK_FILE.FILE_NAME, fileName)
            .set(BOOK_FILE.FILE_SUB_PATH, "/files")
            .set(BOOK_FILE.BOOK_TYPE, bookType)
            .set(BOOK_FILE.IS_BOOK, 1.toByte())
        if (sizeKb != null) insert.set(BOOK_FILE.FILE_SIZE_KB, sizeKb)
        if (durationSeconds != null) insert.set(BOOK_FILE.DURATION_SECONDS, durationSeconds)
        if (codec != null) insert.set(BOOK_FILE.CODEC, codec)
        if (bitrate != null) insert.set(BOOK_FILE.BITRATE, bitrate)
        if (chapterCount != null) insert.set(BOOK_FILE.CHAPTER_COUNT, chapterCount)
        insert.execute()
    }

    private fun insertProgress(
        userId: Long, bookId: Long, readStatus: String? = null, rating: Int? = null,
        epubPercent: Double? = null, koboPercent: Double? = null
    ) {
        val insert = dsl.insertInto(USER_BOOK_PROGRESS)
            .set(USER_BOOK_PROGRESS.USER_ID, userId)
            .set(USER_BOOK_PROGRESS.BOOK_ID, bookId)
        if (readStatus != null) insert.set(USER_BOOK_PROGRESS.READ_STATUS, readStatus)
        if (rating != null) insert.set(USER_BOOK_PROGRESS.PERSONAL_RATING, rating.toByte())
        if (epubPercent != null) insert.set(USER_BOOK_PROGRESS.EPUB_PROGRESS_PERCENT, epubPercent)
        if (koboPercent != null) insert.set(USER_BOOK_PROGRESS.KOBO_PROGRESS_PERCENT, koboPercent)
        insert.execute()
    }

    private fun seriesBook(seriesName: String, number: Double, total: Int? = null): Long {
        val bookId = insertBook(lib1Id, addedOn = LocalDateTime.now().minusDays(200))
        insertMetadata(
            bookId, title = "$seriesName #$number",
            seriesName = seriesName, seriesNumber = number, seriesTotal = total
        )
        return bookId
    }

    private fun linkAuthor(bookId: Long, name: String) {
        val id = dsl.insertInto(AUTHOR).set(AUTHOR.NAME, name)
            .returningResult(AUTHOR.ID).fetchOne()!!.get(AUTHOR.ID)!!
        dsl.insertInto(BOOK_METADATA_AUTHOR_MAPPING)
            .set(BOOK_METADATA_AUTHOR_MAPPING.BOOK_ID, bookId)
            .set(BOOK_METADATA_AUTHOR_MAPPING.AUTHOR_ID, id).execute()
    }

    private fun linkCategory(bookId: Long, name: String) {
        val id = dsl.insertInto(CATEGORY).set(CATEGORY.NAME, name)
            .returningResult(CATEGORY.ID).fetchOne()!!.get(CATEGORY.ID)!!
        dsl.insertInto(BOOK_METADATA_CATEGORY_MAPPING)
            .set(BOOK_METADATA_CATEGORY_MAPPING.BOOK_ID, bookId)
            .set(BOOK_METADATA_CATEGORY_MAPPING.CATEGORY_ID, id).execute()
    }

    private fun linkMood(bookId: Long, name: String) {
        val id = dsl.insertInto(MOOD).set(MOOD.NAME, name)
            .returningResult(MOOD.ID).fetchOne()!!.get(MOOD.ID)!!
        dsl.insertInto(BOOK_METADATA_MOOD_MAPPING)
            .set(BOOK_METADATA_MOOD_MAPPING.BOOK_ID, bookId)
            .set(BOOK_METADATA_MOOD_MAPPING.MOOD_ID, id).execute()
    }

    private fun linkTag(bookId: Long, name: String) {
        val id = dsl.insertInto(TAG).set(TAG.NAME, name)
            .returningResult(TAG.ID).fetchOne()!!.get(TAG.ID)!!
        dsl.insertInto(BOOK_METADATA_TAG_MAPPING)
            .set(BOOK_METADATA_TAG_MAPPING.BOOK_ID, bookId)
            .set(BOOK_METADATA_TAG_MAPPING.TAG_ID, id).execute()
    }
}

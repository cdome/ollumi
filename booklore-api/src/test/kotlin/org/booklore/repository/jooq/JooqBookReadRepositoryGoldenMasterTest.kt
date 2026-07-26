package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.mapper.BookMapper
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
import org.booklore.jooq.tables.ComicCharacter.COMIC_CHARACTER
import org.booklore.jooq.tables.ComicCreator.COMIC_CREATOR
import org.booklore.jooq.tables.ComicLocation.COMIC_LOCATION
import org.booklore.jooq.tables.ComicMetadata.COMIC_METADATA
import org.booklore.jooq.tables.ComicMetadataCharacterMapping.COMIC_METADATA_CHARACTER_MAPPING
import org.booklore.jooq.tables.ComicMetadataCreatorMapping.COMIC_METADATA_CREATOR_MAPPING
import org.booklore.jooq.tables.ComicMetadataLocationMapping.COMIC_METADATA_LOCATION_MAPPING
import org.booklore.jooq.tables.ComicMetadataTeamMapping.COMIC_METADATA_TEAM_MAPPING
import org.booklore.jooq.tables.ComicTeam.COMIC_TEAM
import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.LibraryPath.LIBRARY_PATH
import org.booklore.jooq.tables.Mood.MOOD
import org.booklore.jooq.tables.Shelf.SHELF
import org.booklore.jooq.tables.Tag.TAG
import org.booklore.jooq.tables.Users.USERS
import org.booklore.model.entity.BookEntity
import org.booklore.repository.BookRepository
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Golden-master fidelity check: the jOOQ Book read model must produce a Book DTO
 * recursively equal to the MapStruct (BookMapperV2) mapping for the same book.
 * Increment 1 fixture excludes comic metadata, audiobook metadata and reviews.
 */
class JooqBookReadRepositoryGoldenMasterTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqBookReadRepository
    @Autowired private lateinit var bookRepository: BookRepository
    @Autowired private lateinit var bookMapper: BookMapper
    @Autowired private lateinit var dsl: DSLContext

    private var bookId = 0L

    @BeforeEach
    fun setUp() {
        listOf(
            BOOK_SHELF_MAPPING, SHELF, USERS,
            BOOK_METADATA_CATEGORY_MAPPING, CATEGORY,
            BOOK_METADATA_MOOD_MAPPING, MOOD,
            BOOK_METADATA_TAG_MAPPING, TAG,
            BOOK_METADATA_AUTHOR_MAPPING, AUTHOR,
            COMIC_METADATA_CHARACTER_MAPPING, COMIC_CHARACTER,
            COMIC_METADATA_TEAM_MAPPING, COMIC_TEAM,
            COMIC_METADATA_LOCATION_MAPPING, COMIC_LOCATION,
            COMIC_METADATA_CREATOR_MAPPING, COMIC_CREATOR,
            COMIC_METADATA,
            BOOK_FILE, BOOK_METADATA, BOOK, LIBRARY_PATH, LIBRARY
        ).forEach { dsl.deleteFrom(it).execute() }

        val userId = dsl.insertInto(USERS)
            .set(USERS.USERNAME, "alice").set(USERS.PASSWORD_HASH, "h")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte()).set(USERS.NAME, "alice")
            .returningResult(USERS.ID).fetchOne()!!.get(USERS.ID)!!

        val libId = dsl.insertInto(LIBRARY).set(LIBRARY.NAME, "Main")
            .set(LIBRARY.FORMAT_PRIORITY, """["PDF","EPUB"]""")
            .returningResult(LIBRARY.ID).fetchOne()!!.get(LIBRARY.ID)!!
        val pathId = dsl.insertInto(LIBRARY_PATH).set(LIBRARY_PATH.LIBRARY_ID, libId).set(LIBRARY_PATH.PATH, "/books/main")
            .returningResult(LIBRARY_PATH.ID).fetchOne()!!.get(LIBRARY_PATH.ID)!!

        bookId = dsl.insertInto(BOOK)
            .set(BOOK.LIBRARY_ID, libId)
            .set(BOOK.LIBRARY_PATH_ID, pathId)
            .set(BOOK.ADDED_ON, LocalDateTime.of(2026, 1, 5, 12, 30))
            .set(BOOK.METADATA_MATCH_SCORE, 0.87)
            .set(BOOK.IS_PHYSICAL, 0.toByte())
            .returningResult(BOOK.ID).fetchOne()!!.get(BOOK.ID)!!

        dsl.insertInto(BOOK_METADATA)
            .set(BOOK_METADATA.BOOK_ID, bookId)
            .set(BOOK_METADATA.TITLE, "The Hobbit")
            .set(BOOK_METADATA.SUBTITLE, "There and Back Again")
            .set(BOOK_METADATA.PUBLISHER, "Allen & Unwin")
            .set(BOOK_METADATA.PUBLISHED_DATE, LocalDate.of(1937, 9, 21))
            .set(BOOK_METADATA.DESCRIPTION, "A hobbit's journey")
            .set(BOOK_METADATA.SERIES_NAME, "LOTR")
            .set(BOOK_METADATA.SERIES_NUMBER, 0.5)
            .set(BOOK_METADATA.SERIES_TOTAL, 3)
            .set(BOOK_METADATA.ISBN_13, "9780261103344")
            .set(BOOK_METADATA.ISBN_10, "0261103342")
            .set(BOOK_METADATA.PAGE_COUNT, 310)
            .set(BOOK_METADATA.LANGUAGE, "en")
            .set(BOOK_METADATA.NARRATOR, "Rob Inglis")
            .set(BOOK_METADATA.ABRIDGED, 0.toByte())
            .set(BOOK_METADATA.GOODREADS_RATING, 4.28)
            .set(BOOK_METADATA.GOODREADS_REVIEW_COUNT, 123)
            .set(BOOK_METADATA.AMAZON_RATING, 4.5)
            .set(BOOK_METADATA.ASIN, "B0000ASIN")
            .set(BOOK_METADATA.COVER_UPDATED_ON, LocalDateTime.of(2026, 2, 1, 8, 0))
            .set(BOOK_METADATA.AGE_RATING, 12)
            .set(BOOK_METADATA.CONTENT_RATING, "PG")
            .set(BOOK_METADATA.TITLE_LOCKED, 1.toByte())
            .set(BOOK_METADATA.SERIES_NAME_LOCKED, 0.toByte())
            .execute()

        // Single-element collections: the reference @EntityGraph join-fetches many
        // collections without DISTINCT, so 2+ elements in any of them would multiply
        // the bookFiles list (a latent JPA cartesian-product bug the jOOQ model avoids).
        linkAuthor(insertAuthor("Tolkien"), 0)
        linkCategory(insertCategory("Fantasy"))
        linkMood(insertMood("Cozy"))
        linkTag(insertTag("Classic"))

        val shelfId = dsl.insertInto(SHELF)
            .set(SHELF.USER_ID, userId).set(SHELF.NAME, "Favorites").set(SHELF.ICON, "star")
            .set(SHELF.IS_PUBLIC, 1.toByte())
            .returningResult(SHELF.ID).fetchOne()!!.get(SHELF.ID)!!
        dsl.insertInto(BOOK_SHELF_MAPPING).set(BOOK_SHELF_MAPPING.BOOK_ID, bookId).set(BOOK_SHELF_MAPPING.SHELF_ID, shelfId).execute()

        insertFile("hobbit.epub", "EPUB", isBook = true)
        insertFile("hobbit.pdf", "PDF", isBook = true)
        insertFile("notes.txt", null, isBook = false)
        insertAudiobookFile()

        insertComicMetadata()
    }

    private fun insertAudiobookFile() {
        dsl.insertInto(BOOK_FILE)
            .set(BOOK_FILE.BOOK_ID, bookId)
            .set(BOOK_FILE.FILE_NAME, "hobbit.m4b")
            .set(BOOK_FILE.FILE_SUB_PATH, "sub")
            .set(BOOK_FILE.BOOK_TYPE, "AUDIOBOOK")
            .set(BOOK_FILE.IS_BOOK, 1.toByte())
            .set(BOOK_FILE.ADDED_ON, LocalDateTime.of(2026, 1, 6, 10, 0))
            .set(BOOK_FILE.DURATION_SECONDS, 57_600L)
            .set(BOOK_FILE.BITRATE, 128)
            .set(BOOK_FILE.SAMPLE_RATE, 44_100)
            .set(BOOK_FILE.CHANNELS, 2)
            .set(BOOK_FILE.CODEC, "aac")
            .set(BOOK_FILE.CHAPTER_COUNT, 2)
            .set(
                BOOK_FILE.CHAPTERS_JSON,
                """[{"index":1,"title":"Chapter 1","startTimeMs":0,"endTimeMs":1000,"durationMs":1000},{"index":2,"title":"Chapter 2","startTimeMs":1000,"endTimeMs":3000,"durationMs":2000}]"""
            )
            .execute()
    }

    private fun insertComicMetadata() {
        dsl.insertInto(COMIC_METADATA)
            .set(COMIC_METADATA.BOOK_ID, bookId)
            .set(COMIC_METADATA.ISSUE_NUMBER, "1")
            .set(COMIC_METADATA.VOLUME_NAME, "Vol 1")
            .set(COMIC_METADATA.VOLUME_NUMBER, 1)
            .set(COMIC_METADATA.STORY_ARC, "Origins")
            .set(COMIC_METADATA.IMPRINT, "Vertigo")
            .set(COMIC_METADATA.FORMAT, "Trade Paperback")
            .set(COMIC_METADATA.BLACK_AND_WHITE, 0.toByte())
            .set(COMIC_METADATA.MANGA, 1.toByte())
            .set(COMIC_METADATA.READING_DIRECTION, "LTR")
            .set(COMIC_METADATA.WEB_LINK, "https://example.com")
            .set(COMIC_METADATA.NOTES, "note")
            .set(COMIC_METADATA.ISSUE_NUMBER_LOCKED, 1.toByte())
            .set(COMIC_METADATA.MANGA_LOCKED, 0.toByte())
            .execute()

        val charId = dsl.insertInto(COMIC_CHARACTER).set(COMIC_CHARACTER.NAME, "Batman")
            .returningResult(COMIC_CHARACTER.ID).fetchOne()!!.get(COMIC_CHARACTER.ID)!!
        dsl.insertInto(COMIC_METADATA_CHARACTER_MAPPING)
            .set(COMIC_METADATA_CHARACTER_MAPPING.BOOK_ID, bookId)
            .set(COMIC_METADATA_CHARACTER_MAPPING.CHARACTER_ID, charId).execute()

        val teamId = dsl.insertInto(COMIC_TEAM).set(COMIC_TEAM.NAME, "Justice League")
            .returningResult(COMIC_TEAM.ID).fetchOne()!!.get(COMIC_TEAM.ID)!!
        dsl.insertInto(COMIC_METADATA_TEAM_MAPPING)
            .set(COMIC_METADATA_TEAM_MAPPING.BOOK_ID, bookId)
            .set(COMIC_METADATA_TEAM_MAPPING.TEAM_ID, teamId).execute()

        val locId = dsl.insertInto(COMIC_LOCATION).set(COMIC_LOCATION.NAME, "Gotham")
            .returningResult(COMIC_LOCATION.ID).fetchOne()!!.get(COMIC_LOCATION.ID)!!
        dsl.insertInto(COMIC_METADATA_LOCATION_MAPPING)
            .set(COMIC_METADATA_LOCATION_MAPPING.BOOK_ID, bookId)
            .set(COMIC_METADATA_LOCATION_MAPPING.LOCATION_ID, locId).execute()

        val creatorId = dsl.insertInto(COMIC_CREATOR).set(COMIC_CREATOR.NAME, "Alan Moore")
            .returningResult(COMIC_CREATOR.ID).fetchOne()!!.get(COMIC_CREATOR.ID)!!
        dsl.insertInto(COMIC_METADATA_CREATOR_MAPPING)
            .set(COMIC_METADATA_CREATOR_MAPPING.BOOK_ID, bookId)
            .set(COMIC_METADATA_CREATOR_MAPPING.CREATOR_ID, creatorId)
            .set(COMIC_METADATA_CREATOR_MAPPING.ROLE, "PENCILLER").execute()
    }

    @Test
    @Transactional
    fun `jooq book read model matches MapStruct`() {
        val entity: BookEntity = bookRepository.findAllWithMetadataByIds(setOf(bookId)).single()
        val candidate = repository.findByIds(listOf(bookId)).single()

        // BookMapper.toBook is the live web Book mapper (write-flow notifications still use it);
        // the jOOQ read model must reproduce it exactly.
        assertThat(candidate).usingRecursiveComparison().ignoringCollectionOrder()
            .isEqualTo(bookMapper.toBook(entity))
    }

    @Test
    fun `authors are ordered by sort order`() {
        dsl.insertInto(AUTHOR).set(AUTHOR.NAME, "Orwell")
            .returningResult(AUTHOR.ID).fetchOne()!!.get(AUTHOR.ID)!!
            .let { linkAuthor(it, 1) }

        val candidate = repository.findByIds(listOf(bookId)).single()
        assertThat(candidate.metadata.authors).containsExactly("Tolkien", "Orwell")
    }

    private fun insertAuthor(name: String): Long =
        dsl.insertInto(AUTHOR).set(AUTHOR.NAME, name).returningResult(AUTHOR.ID).fetchOne()!!.get(AUTHOR.ID)!!

    private fun insertCategory(name: String): Long =
        dsl.insertInto(CATEGORY).set(CATEGORY.NAME, name).returningResult(CATEGORY.ID).fetchOne()!!.get(CATEGORY.ID)!!

    private fun insertMood(name: String): Long =
        dsl.insertInto(MOOD).set(MOOD.NAME, name).returningResult(MOOD.ID).fetchOne()!!.get(MOOD.ID)!!

    private fun insertTag(name: String): Long =
        dsl.insertInto(TAG).set(TAG.NAME, name).returningResult(TAG.ID).fetchOne()!!.get(TAG.ID)!!

    private fun linkAuthor(authorId: Long, sortOrder: Int) {
        dsl.insertInto(BOOK_METADATA_AUTHOR_MAPPING)
            .set(BOOK_METADATA_AUTHOR_MAPPING.BOOK_ID, bookId)
            .set(BOOK_METADATA_AUTHOR_MAPPING.AUTHOR_ID, authorId)
            .set(BOOK_METADATA_AUTHOR_MAPPING.SORT_ORDER, sortOrder).execute()
    }

    private fun linkCategory(categoryId: Long) {
        dsl.insertInto(BOOK_METADATA_CATEGORY_MAPPING)
            .set(BOOK_METADATA_CATEGORY_MAPPING.BOOK_ID, bookId)
            .set(BOOK_METADATA_CATEGORY_MAPPING.CATEGORY_ID, categoryId).execute()
    }

    private fun linkMood(moodId: Long) {
        dsl.insertInto(BOOK_METADATA_MOOD_MAPPING)
            .set(BOOK_METADATA_MOOD_MAPPING.BOOK_ID, bookId)
            .set(BOOK_METADATA_MOOD_MAPPING.MOOD_ID, moodId).execute()
    }

    private fun linkTag(tagId: Long) {
        dsl.insertInto(BOOK_METADATA_TAG_MAPPING)
            .set(BOOK_METADATA_TAG_MAPPING.BOOK_ID, bookId)
            .set(BOOK_METADATA_TAG_MAPPING.TAG_ID, tagId).execute()
    }

    private fun insertFile(fileName: String, bookType: String?, isBook: Boolean) {
        val insert = dsl.insertInto(BOOK_FILE)
            .set(BOOK_FILE.BOOK_ID, bookId)
            .set(BOOK_FILE.FILE_NAME, fileName)
            .set(BOOK_FILE.FILE_SUB_PATH, "sub")
            .set(BOOK_FILE.IS_BOOK, if (isBook) 1.toByte() else 0.toByte())
            .set(BOOK_FILE.ADDED_ON, LocalDateTime.of(2026, 1, 6, 10, 0))
        if (bookType != null) insert.set(BOOK_FILE.BOOK_TYPE, bookType)
        insert.execute()
    }
}

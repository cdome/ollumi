package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.booklore.jooq.tables.Book.BOOK
import org.booklore.jooq.tables.CbxViewerPreference.CBX_VIEWER_PREFERENCE
import org.booklore.jooq.tables.EbookViewerPreference.EBOOK_VIEWER_PREFERENCE
import org.booklore.jooq.tables.Library.LIBRARY
import org.booklore.jooq.tables.NewPdfViewerPreference.NEW_PDF_VIEWER_PREFERENCE
import org.booklore.jooq.tables.PdfViewerPreference.PDF_VIEWER_PREFERENCE
import org.booklore.jooq.tables.Users.USERS
import org.booklore.model.dto.CbxViewerPreferences
import org.booklore.model.dto.EbookViewerPreferences
import org.booklore.model.dto.NewPdfViewerPreferences
import org.booklore.model.enums.CbxBackgroundColor
import org.booklore.model.enums.CbxPageFitMode
import org.booklore.model.enums.CbxPageScrollMode
import org.booklore.model.enums.CbxPageSpread
import org.booklore.model.enums.CbxPageViewMode
import org.booklore.model.enums.NewPdfBackgroundColor
import org.booklore.model.enums.NewPdfPageFitMode
import org.booklore.model.enums.NewPdfPageScrollMode
import org.booklore.model.enums.NewPdfPageSpread
import org.booklore.model.enums.NewPdfPageViewMode
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

class JooqViewerPreferenceRepositoriesTest : AbstractIntegrationTest() {

    @Autowired private lateinit var pdfRepo: JooqPdfViewerPreferenceRepository
    @Autowired private lateinit var cbxRepo: JooqCbxViewerPreferenceRepository
    @Autowired private lateinit var newPdfRepo: JooqNewPdfViewerPreferenceRepository
    @Autowired private lateinit var ebookRepo: JooqEbookViewerPreferenceRepository
    @Autowired private lateinit var dsl: DSLContext

    private var userId: Long = 0
    private var bookId: Long = 0

    @BeforeEach
    fun setUp() {
        listOf(PDF_VIEWER_PREFERENCE, CBX_VIEWER_PREFERENCE, NEW_PDF_VIEWER_PREFERENCE, EBOOK_VIEWER_PREFERENCE)
            .forEach { dsl.deleteFrom(it).execute() }
        dsl.deleteFrom(BOOK).execute()
        dsl.deleteFrom(LIBRARY).execute()
        dsl.deleteFrom(USERS).execute()

        userId = dsl.insertInto(USERS)
            .set(USERS.USERNAME, "reader")
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte())
            .set(USERS.NAME, "reader")
            .returningResult(USERS.ID).fetchOne()!!.get(USERS.ID)!!
        val libId = dsl.insertInto(LIBRARY)
            .set(LIBRARY.NAME, "Library")
            .returningResult(LIBRARY.ID).fetchOne()!!.get(LIBRARY.ID)!!
        bookId = dsl.insertInto(BOOK)
            .set(BOOK.LIBRARY_ID, libId)
            .set(BOOK.ADDED_ON, LocalDateTime.now())
            .returningResult(BOOK.ID).fetchOne()!!.get(BOOK.ID)!!
    }

    @Test
    fun `pdf upsert inserts then updates, find returns dto`() {
        assertThat(pdfRepo.findByBookIdAndUserId(bookId, userId)).isNull()

        pdfRepo.upsert(bookId, userId, "1.5", "odd")
        var dto = pdfRepo.findByBookIdAndUserId(bookId, userId)!!
        assertThat(dto.bookId).isEqualTo(bookId)
        assertThat(dto.zoom).isEqualTo("1.5")
        assertThat(dto.spread).isEqualTo("odd")

        pdfRepo.upsert(bookId, userId, "2.0", "even")
        dto = pdfRepo.findByBookIdAndUserId(bookId, userId)!!
        assertThat(dto.zoom).isEqualTo("2.0")
        assertThat(dto.spread).isEqualTo("even")
        assertThat(dsl.fetchCount(PDF_VIEWER_PREFERENCE)).isEqualTo(1)
    }

    @Test
    fun `ebook upsert round-trips byte-boolean and double-float conversions`() {
        val prefs = EbookViewerPreferences.builder()
            .fontFamily("serif").fontSize(18)
            .gap(0.1f).hyphenate(true).isDark(false).justify(true)
            .lineHeight(1.7f).maxBlockSize(800).maxColumnCount(3).maxInlineSize(1200)
            .theme("dark").flow("paginated").build()

        ebookRepo.upsert(bookId, userId, prefs)
        val dto = ebookRepo.findByBookIdAndUserId(bookId, userId)!!

        assertThat(dto.bookId).isEqualTo(bookId)
        assertThat(dto.userId).isEqualTo(userId)
        assertThat(dto.fontFamily).isEqualTo("serif")
        assertThat(dto.fontSize).isEqualTo(18)
        assertThat(dto.gap).isCloseTo(0.1f, within(0.0001f))
        assertThat(dto.hyphenate).isTrue()
        assertThat(dto.isDark).isFalse()
        assertThat(dto.justify).isTrue()
        assertThat(dto.lineHeight).isCloseTo(1.7f, within(0.0001f))
        assertThat(dto.maxBlockSize).isEqualTo(800)
        assertThat(dto.theme).isEqualTo("dark")
        assertThat(dto.flow).isEqualTo("paginated")

        // update path
        val updatedPrefs = EbookViewerPreferences.builder()
            .fontFamily("mono").fontSize(18)
            .gap(0.1f).hyphenate(true).isDark(true).justify(true)
            .lineHeight(1.7f).maxBlockSize(800).maxColumnCount(3).maxInlineSize(1200)
            .theme("dark").flow("paginated").build()
        ebookRepo.upsert(bookId, userId, updatedPrefs)
        val updated = ebookRepo.findByBookIdAndUserId(bookId, userId)!!
        assertThat(updated.fontFamily).isEqualTo("mono")
        assertThat(updated.isDark).isTrue()
        assertThat(dsl.fetchCount(EBOOK_VIEWER_PREFERENCE)).isEqualTo(1)
    }

    @Test
    fun `cbx upsert round-trips enums as strings`() {
        val prefs = CbxViewerPreferences.builder()
            .pageSpread(CbxPageSpread.EVEN)
            .pageViewMode(CbxPageViewMode.SINGLE_PAGE)
            .fitMode(CbxPageFitMode.ACTUAL_SIZE)
            .scrollMode(CbxPageScrollMode.PAGINATED)
            .backgroundColor(CbxBackgroundColor.GRAY)
            .build()

        cbxRepo.upsert(bookId, userId, prefs)
        val dto = cbxRepo.findByBookIdAndUserId(bookId, userId)!!

        assertThat(dto.pageSpread).isEqualTo(CbxPageSpread.EVEN)
        assertThat(dto.pageViewMode).isEqualTo(CbxPageViewMode.SINGLE_PAGE)
        assertThat(dto.fitMode).isEqualTo(CbxPageFitMode.ACTUAL_SIZE)
        assertThat(dto.scrollMode).isEqualTo(CbxPageScrollMode.PAGINATED)
        assertThat(dto.backgroundColor).isEqualTo(CbxBackgroundColor.GRAY)

        assertThat(dsl.select(CBX_VIEWER_PREFERENCE.SPREAD).from(CBX_VIEWER_PREFERENCE)
            .fetchOne(CBX_VIEWER_PREFERENCE.SPREAD)).isEqualTo("EVEN")
    }

    @Test
    fun `new pdf upsert round-trips enums as strings`() {
        val prefs = NewPdfViewerPreferences.builder()
            .pageSpread(NewPdfPageSpread.EVEN)
            .pageViewMode(NewPdfPageViewMode.SINGLE_PAGE)
            .fitMode(NewPdfPageFitMode.ACTUAL_SIZE)
            .scrollMode(NewPdfPageScrollMode.PAGINATED)
            .backgroundColor(NewPdfBackgroundColor.GRAY)
            .build()

        newPdfRepo.upsert(bookId, userId, prefs)
        val dto = newPdfRepo.findByBookIdAndUserId(bookId, userId)!!

        assertThat(dto.pageSpread).isEqualTo(NewPdfPageSpread.EVEN)
        assertThat(dto.pageViewMode).isEqualTo(NewPdfPageViewMode.SINGLE_PAGE)
        assertThat(dto.fitMode).isEqualTo(NewPdfPageFitMode.ACTUAL_SIZE)
        assertThat(dto.scrollMode).isEqualTo(NewPdfPageScrollMode.PAGINATED)
        assertThat(dto.backgroundColor).isEqualTo(NewPdfBackgroundColor.GRAY)
    }

    @Test
    fun `find enforces user scoping`() {
        pdfRepo.upsert(bookId, userId, "1.0", "odd")
        assertThat(pdfRepo.findByBookIdAndUserId(bookId, userId + 999)).isNull()
    }
}

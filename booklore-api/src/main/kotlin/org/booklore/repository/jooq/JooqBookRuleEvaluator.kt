package org.booklore.repository.jooq

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
import org.booklore.jooq.tables.ComicMetadataCharacterMapping.COMIC_METADATA_CHARACTER_MAPPING
import org.booklore.jooq.tables.ComicMetadataCreatorMapping.COMIC_METADATA_CREATOR_MAPPING
import org.booklore.jooq.tables.ComicMetadataLocationMapping.COMIC_METADATA_LOCATION_MAPPING
import org.booklore.jooq.tables.ComicMetadataTeamMapping.COMIC_METADATA_TEAM_MAPPING
import org.booklore.jooq.tables.Mood.MOOD
import org.booklore.jooq.tables.Tag.TAG
import org.booklore.jooq.tables.UserBookProgress.USER_BOOK_PROGRESS
import org.booklore.model.dto.GroupRule
import org.booklore.model.dto.Rule
import org.booklore.model.dto.RuleField
import org.booklore.model.dto.RuleOperator
import org.booklore.model.enums.ComicCreatorRole
import org.jooq.Condition
import org.jooq.Field
import org.jooq.impl.DSL.*
import org.jooq.impl.SQLDataType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Translates Magic Shelf rule trees into jOOQ [Condition]s.
 *
 * The produced condition assumes a driving query of the shape used by
 * [JooqMagicShelfBookRepository]: BOOK left-joined with BOOK_METADATA,
 * USER_BOOK_PROGRESS and BOOK_FILE (multi-valued rule fields use correlated
 * EXISTS subqueries and are join-independent).
 */
@Component
class JooqBookRuleEvaluator(private val objectMapper: ObjectMapper) {

    private val log = LoggerFactory.getLogger(JooqBookRuleEvaluator::class.java)

    private val bm = BOOK_METADATA
    private val ubp = USER_BOOK_PROGRESS
    private val bf = BOOK_FILE

    fun toCondition(groupRule: GroupRule, userId: Long?): Condition {
        // Mirrors the JPA spec: LEFT JOIN over all progress rows, filtered to
        // "no progress or this user's progress" at the top level.
        val userCondition = ubp.USER_ID.isNull.or(userEquals(userId))
        return userCondition.and(buildGroup(groupRule, userId))
    }

    private fun userEquals(userId: Long?): Condition =
        if (userId != null) ubp.USER_ID.eq(userId) else falseCondition()

    // ========================================================================
    // Group / rule tree
    // ========================================================================

    private fun buildGroup(group: GroupRule, userId: Long?): Condition {
        val rules = group.rules ?: return noCondition()
        if (rules.isEmpty()) return noCondition()

        val conditions = mutableListOf<Condition>()
        for (ruleObj in rules) {
            if (ruleObj == null) continue
            val ruleMap = objectMapper.convertValue(ruleObj, Map::class.java)
            if ("group" == ruleMap["type"]) {
                conditions.add(buildGroup(objectMapper.convertValue(ruleObj, GroupRule::class.java), userId))
            } else {
                try {
                    val rule = objectMapper.convertValue(ruleObj, Rule::class.java)
                    buildRule(rule, userId)?.let { conditions.add(it) }
                } catch (e: Exception) {
                    log.error("Failed to parse rule: {}, error: {}", ruleObj, e.message, e)
                }
            }
        }

        if (conditions.isEmpty()) return noCondition()
        return if (group.join == org.booklore.model.dto.JoinType.AND) and(conditions) else or(conditions)
    }

    private fun buildRule(rule: Rule, userId: Long?): Condition? {
        val field = rule.field ?: return null
        rule.operator ?: return null

        if (field == RuleField.METADATA_PRESENCE) return metadataPresence(rule)
        if (field in COMPOSITE_FIELDS) return compositeField(rule, userId)

        return when (rule.operator!!) {
            RuleOperator.EQUALS -> buildEquals(rule)
            RuleOperator.NOT_EQUALS -> buildNotEquals(rule)
            RuleOperator.CONTAINS -> buildLike(rule) { "%$it%" }
            RuleOperator.DOES_NOT_CONTAIN -> {
                val notContains = not(buildLike(rule) { "%$it%" })
                if (field == RuleField.READ_STATUS) ubp.READ_STATUS.isNull.or(notContains) else notContains
            }
            RuleOperator.STARTS_WITH -> buildLike(rule) { "$it%" }
            RuleOperator.ENDS_WITH -> buildLike(rule) { "%$it" }
            RuleOperator.GREATER_THAN -> buildComparison(rule, ComparisonOp.GT)
            RuleOperator.GREATER_THAN_EQUAL_TO -> buildComparison(rule, ComparisonOp.GE)
            RuleOperator.LESS_THAN -> buildComparison(rule, ComparisonOp.LT)
            RuleOperator.LESS_THAN_EQUAL_TO -> buildComparison(rule, ComparisonOp.LE)
            RuleOperator.IN_BETWEEN -> buildInBetween(rule)
            RuleOperator.IS_EMPTY -> buildIsEmpty(rule)
            RuleOperator.IS_NOT_EMPTY -> not(buildIsEmpty(rule))
            RuleOperator.INCLUDES_ANY -> buildIncludes(rule, includesAll = false)
            RuleOperator.EXCLUDES_ALL -> buildExcludesAll(rule)
            RuleOperator.INCLUDES_ALL -> buildIncludes(rule, includesAll = true)
            RuleOperator.WITHIN_LAST -> buildWithinLast(rule)
            RuleOperator.OLDER_THAN -> buildOlderThan(rule)
            RuleOperator.THIS_PERIOD -> buildThisPeriod(rule)
        }
    }

    // ========================================================================
    // Simple operators
    // ========================================================================

    private fun buildEquals(rule: Rule): Condition {
        val field = rule.field!!
        if (isArrayField(field)) return arrayFieldIn(field, toStringList(rule.value))

        val f = fieldFor(field) ?: return noCondition()
        val value = normalizeValue(rule.value, field) ?: return noCondition()

        return when {
            value is Boolean -> booleanField(f).eq(value)
            value is LocalDate -> f.coerce(SQLDataType.LOCALDATE).eq(value)
            value is LocalDateTime -> f.coerce(SQLDataType.LOCALDATETIME).eq(value)
            field == RuleField.READ_STATUS ->
                if ("UNSET" == value.toString()) ubp.READ_STATUS.isNull
                else ubp.READ_STATUS.eq(value.toString())
            value is Number -> f.coerce(SQLDataType.DOUBLE).eq(value.toDouble())
            else -> lower(f.coerce(SQLDataType.VARCHAR)).eq(value.toString().lowercase())
        }
    }

    private fun buildNotEquals(rule: Rule): Condition {
        val negated = not(buildEquals(rule))
        if (rule.field == RuleField.READ_STATUS && "UNSET" != rule.value?.toString()) {
            return ubp.READ_STATUS.isNull.or(negated)
        }
        return negated
    }

    private fun buildLike(rule: Rule, pattern: (String) -> String): Condition {
        val field = rule.field!!
        val likePattern = pattern(escapeLike(rule.value.toString().lowercase()))
        if (isArrayField(field)) {
            return arrayFieldMatch(field) { name -> lower(name).like(likePattern) }
        }
        val f = fieldFor(field) ?: return noCondition()
        return lower(f.coerce(SQLDataType.VARCHAR)).like(likePattern)
    }

    private enum class ComparisonOp { GT, GE, LT, LE }

    private fun buildComparison(rule: Rule, op: ComparisonOp): Condition {
        val f = fieldFor(rule.field!!) ?: return noCondition()
        return when (val value = normalizeValue(rule.value, rule.field!!)) {
            is LocalDate -> f.coerce(SQLDataType.LOCALDATE).compare(op, value)
            is LocalDateTime -> f.coerce(SQLDataType.LOCALDATETIME).compare(op, value)
            is Number -> f.coerce(SQLDataType.DOUBLE).compare(op, value.toDouble())
            else -> noCondition()
        }
    }

    private fun <T> Field<T>.compare(op: ComparisonOp, value: T): Condition = when (op) {
        ComparisonOp.GT -> gt(value)
        ComparisonOp.GE -> ge(value)
        ComparisonOp.LT -> lt(value)
        ComparisonOp.LE -> le(value)
    }

    private fun buildInBetween(rule: Rule): Condition {
        val f = fieldFor(rule.field!!) ?: return noCondition()
        val start = normalizeValue(rule.valueStart, rule.field!!)
        val end = normalizeValue(rule.valueEnd, rule.field!!)
        if (start == null || end == null) return noCondition()

        return when {
            start is LocalDate && end is LocalDate ->
                f.coerce(SQLDataType.LOCALDATE).between(start, end)
            start is LocalDateTime && end is LocalDateTime ->
                f.coerce(SQLDataType.LOCALDATETIME).between(start, end)
            start is Number && end is Number ->
                f.coerce(SQLDataType.DOUBLE).between(start.toDouble(), end.toDouble())
            else -> noCondition()
        }
    }

    private fun buildIsEmpty(rule: Rule): Condition {
        val field = rule.field!!
        if (isArrayField(field)) return not(arrayFieldHasAny(field))
        val f = fieldFor(field) ?: return noCondition()
        return f.isNull.or(trim(f.coerce(SQLDataType.VARCHAR)).eq(""))
    }

    private fun buildIncludes(rule: Rule, includesAll: Boolean): Condition {
        val field = rule.field!!
        val values = toStringList(rule.value)
        if (isArrayField(field)) return arrayFieldCondition(field, values, includesAll)
        return fieldIn(field, values)
    }

    private fun buildExcludesAll(rule: Rule): Condition {
        val field = rule.field!!
        val values = toStringList(rule.value)
        if (isArrayField(field)) return not(arrayFieldIn(field, values))

        val negated = not(fieldIn(field, values))
        if (field == RuleField.READ_STATUS && values.none { it == "UNSET" }) {
            return ubp.READ_STATUS.isNull.or(negated)
        }
        return negated
    }

    private fun fieldIn(field: RuleField, values: List<String>): Condition {
        val f = fieldFor(field) ?: return noCondition()

        if (field == RuleField.READ_STATUS) {
            val hasUnset = values.any { it == "UNSET" }
            val nonUnset = values.filter { it != "UNSET" }
            return when {
                hasUnset && nonUnset.isNotEmpty() -> ubp.READ_STATUS.isNull.or(ubp.READ_STATUS.`in`(nonUnset))
                hasUnset -> ubp.READ_STATUS.isNull
                else -> ubp.READ_STATUS.`in`(nonUnset)
            }
        }

        return lower(f.coerce(SQLDataType.VARCHAR)).`in`(values.map { it.lowercase() })
    }

    // ========================================================================
    // Relative date operators
    // ========================================================================

    private fun buildWithinLast(rule: Rule): Condition {
        val f = fieldFor(rule.field!!) ?: return noCondition()
        val threshold = relativeThreshold(rule) ?: return noCondition()
        return if (rule.field == RuleField.PUBLISHED_DATE)
            f.coerce(SQLDataType.LOCALDATE).ge(threshold.toLocalDate())
        else
            f.coerce(SQLDataType.LOCALDATETIME).ge(threshold)
    }

    private fun buildOlderThan(rule: Rule): Condition {
        val f = fieldFor(rule.field!!) ?: return noCondition()
        val threshold = relativeThreshold(rule) ?: return noCondition()
        return if (rule.field == RuleField.PUBLISHED_DATE)
            f.coerce(SQLDataType.LOCALDATE).lt(threshold.toLocalDate())
        else
            f.coerce(SQLDataType.LOCALDATETIME).lt(threshold)
    }

    private fun buildThisPeriod(rule: Rule): Condition {
        val f = fieldFor(rule.field!!) ?: return noCondition()
        val period = rule.value?.toString()?.lowercase() ?: "year"
        val now = LocalDate.now()
        val start = when (period) {
            "week" -> now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            "month" -> now.withDayOfMonth(1)
            else -> now.withDayOfYear(1)
        }
        return if (rule.field == RuleField.PUBLISHED_DATE)
            f.coerce(SQLDataType.LOCALDATE).ge(start)
        else
            f.coerce(SQLDataType.LOCALDATETIME).ge(start.atStartOfDay())
    }

    private fun relativeThreshold(rule: Rule): LocalDateTime? {
        val value = rule.value ?: return null
        val amount = (value as? Number)?.toInt() ?: value.toString().toIntOrNull() ?: return null
        val unit = rule.valueEnd?.toString()?.lowercase() ?: "days"
        val now = LocalDateTime.now()
        return when (unit) {
            "weeks" -> now.minusWeeks(amount.toLong())
            "months" -> now.minusMonths(amount.toLong())
            "years" -> now.minusYears(amount.toLong())
            else -> now.minusDays(amount.toLong())
        }
    }

    // ========================================================================
    // Metadata presence
    // ========================================================================

    private fun metadataPresence(rule: Rule): Condition {
        val isPresent = presenceCondition(rule.value?.toString() ?: "")
        return if (rule.operator == RuleOperator.EQUALS) isPresent else not(isPresent)
    }

    private fun presenceCondition(name: String): Condition = when (name) {
        "thumbnailUrl" -> BOOK.BOOK_COVER_HASH.isNotNull
        "personalRating" -> ubp.PERSONAL_RATING.isNotNull

        "audiobookDuration" -> {
            val f2 = BOOK_FILE.`as`("pres_bf")
            exists(
                selectOne().from(f2)
                    .where(f2.BOOK_ID.eq(BOOK.ID))
                    .and(f2.DURATION_SECONDS.isNotNull)
            )
        }

        "authors" -> mappingExists(BOOK_METADATA_AUTHOR_MAPPING, BOOK_METADATA_AUTHOR_MAPPING.BOOK_ID)
        "categories" -> mappingExists(BOOK_METADATA_CATEGORY_MAPPING, BOOK_METADATA_CATEGORY_MAPPING.BOOK_ID)
        "moods" -> mappingExists(BOOK_METADATA_MOOD_MAPPING, BOOK_METADATA_MOOD_MAPPING.BOOK_ID)
        "tags" -> mappingExists(BOOK_METADATA_TAG_MAPPING, BOOK_METADATA_TAG_MAPPING.BOOK_ID)

        "comicCharacters" -> mappingExists(COMIC_METADATA_CHARACTER_MAPPING, COMIC_METADATA_CHARACTER_MAPPING.BOOK_ID)
        "comicTeams" -> mappingExists(COMIC_METADATA_TEAM_MAPPING, COMIC_METADATA_TEAM_MAPPING.BOOK_ID)
        "comicLocations" -> mappingExists(COMIC_METADATA_LOCATION_MAPPING, COMIC_METADATA_LOCATION_MAPPING.BOOK_ID)

        "comicPencillers" -> creatorRoleExists(ComicCreatorRole.PENCILLER)
        "comicInkers" -> creatorRoleExists(ComicCreatorRole.INKER)
        "comicColorists" -> creatorRoleExists(ComicCreatorRole.COLORIST)
        "comicLetterers" -> creatorRoleExists(ComicCreatorRole.LETTERER)
        "comicCoverArtists" -> creatorRoleExists(ComicCreatorRole.COVER_ARTIST)
        "comicEditors" -> creatorRoleExists(ComicCreatorRole.EDITOR)

        else -> {
            val column = METADATA_STRING_COLUMNS[name]
            val nullableColumn = METADATA_NULLABLE_COLUMNS[name]
            when {
                column != null -> column.isNotNull.and(trim(column).ne(""))
                nullableColumn != null -> nullableColumn.isNotNull
                else -> noCondition()
            }
        }
    }

    private fun mappingExists(table: org.jooq.Table<*>, bookIdField: Field<Long>): Condition =
        exists(selectOne().from(table).where(bookIdField.eq(BOOK.ID)))

    private fun creatorRoleExists(role: ComicCreatorRole): Condition =
        exists(
            selectOne().from(COMIC_METADATA_CREATOR_MAPPING)
                .where(COMIC_METADATA_CREATOR_MAPPING.BOOK_ID.eq(BOOK.ID))
                .and(COMIC_METADATA_CREATOR_MAPPING.ROLE.eq(role.name))
        )

    // ========================================================================
    // Composite series fields
    // ========================================================================

    private fun compositeField(rule: Rule, userId: Long?): Condition {
        val negate = rule.operator == RuleOperator.NOT_EQUALS
        val value = rule.value?.toString()?.lowercase() ?: ""
        val hasSeries = bm.SERIES_NAME.isNotNull.and(trim(bm.SERIES_NAME).ne(""))

        val result = when (rule.field) {
            RuleField.SERIES_STATUS -> hasSeries.and(seriesStatus(value, userId))
            RuleField.SERIES_GAPS -> hasSeries.and(seriesGaps(value))
            RuleField.SERIES_POSITION ->
                hasSeries.and(bm.SERIES_NUMBER.isNotNull).and(seriesPosition(value, userId))
            else -> noCondition()
        }

        return if (negate) not(result) else result
    }

    private fun seriesStatus(value: String, userId: Long?): Condition = when (value) {
        "reading" -> seriesHasReadStatus(userId, listOf("READING", "RE_READING"))
        "not_started" -> not(seriesHasReadStatus(userId, listOf("READ", "READING", "RE_READING", "PARTIALLY_READ")))
        "fully_read" -> seriesAllRead(userId)
        "completed" -> seriesOwnsLastBook()
        "ongoing" -> seriesHasTotal().and(not(seriesOwnsLastBook()))
        else -> noCondition()
    }

    private fun seriesGaps(value: String): Condition = when (value) {
        "any_gap" -> seriesHasAnyGap()
        "missing_first" -> seriesMissingFirst()
        "missing_latest" -> seriesHasTotal().and(not(seriesOwnsLastBook()))
        "duplicate_number" -> seriesHasDuplicateNumber()
        else -> noCondition()
    }

    private fun seriesPosition(value: String, userId: Long?): Condition = when (value) {
        "next_unread" -> isNextUnread(userId)
        "first_in_series" -> bm.SERIES_NUMBER.eq(seriesNumberBound { min(it) })
        "last_in_series" -> bm.SERIES_NUMBER.eq(seriesNumberBound { max(it) })
        else -> noCondition()
    }

    private fun seriesHasReadStatus(userId: Long?, statuses: List<String>): Condition {
        val m2 = BOOK_METADATA.`as`("shr_m")
        val p2 = USER_BOOK_PROGRESS.`as`("shr_p")
        return exists(
            selectOne().from(m2)
                .join(p2).on(p2.BOOK_ID.eq(m2.BOOK_ID))
                .where(m2.SERIES_NAME.eq(bm.SERIES_NAME))
                .and(if (userId != null) p2.USER_ID.eq(userId) else falseCondition())
                .and(p2.READ_STATUS.`in`(statuses))
        )
    }

    private fun seriesAllRead(userId: Long?): Condition {
        val m2 = BOOK_METADATA.`as`("sar_m")
        val p2 = USER_BOOK_PROGRESS.`as`("sar_p")
        val anyNotRead = exists(
            selectOne().from(m2)
                .join(p2).on(p2.BOOK_ID.eq(m2.BOOK_ID))
                .where(m2.SERIES_NAME.eq(bm.SERIES_NAME))
                .and(if (userId != null) p2.USER_ID.eq(userId) else falseCondition())
                .and(p2.READ_STATUS.ne("READ"))
        )
        return seriesHasReadStatus(userId, listOf("READ")).and(not(anyNotRead))
    }

    private fun seriesOwnsLastBook(): Condition {
        val mEx = BOOK_METADATA.`as`("olb_m")
        val mTot = BOOK_METADATA.`as`("olb_t")
        val maxTotal = select(max(mTot.SERIES_TOTAL))
            .from(mTot)
            .where(mTot.SERIES_NAME.eq(bm.SERIES_NAME))
            .and(mTot.SERIES_TOTAL.isNotNull)
        return exists(
            selectOne().from(mEx)
                .where(mEx.SERIES_NAME.eq(bm.SERIES_NAME))
                .and(floor(mEx.SERIES_NUMBER).eq(field(maxTotal).coerce(SQLDataType.DOUBLE)))
        )
    }

    private fun seriesHasTotal(): Condition {
        val m2 = BOOK_METADATA.`as`("sht_m")
        return exists(
            selectOne().from(m2)
                .where(m2.SERIES_NAME.eq(bm.SERIES_NAME))
                .and(m2.SERIES_TOTAL.isNotNull)
        )
    }

    private fun seriesHasAnyGap(): Condition {
        val mC = BOOK_METADATA.`as`("gap_c")
        val mM = BOOK_METADATA.`as`("gap_m")
        val distinctNumbers = select(countDistinct(floor(mC.SERIES_NUMBER)))
            .from(mC)
            .where(mC.SERIES_NAME.eq(bm.SERIES_NAME))
            .and(mC.SERIES_NUMBER.isNotNull)
        val maxNumber = select(max(floor(mM.SERIES_NUMBER)))
            .from(mM)
            .where(mM.SERIES_NAME.eq(bm.SERIES_NAME))
            .and(mM.SERIES_NUMBER.isNotNull)
        return field(distinctNumbers).coerce(SQLDataType.DOUBLE).lt(field(maxNumber).coerce(SQLDataType.DOUBLE))
    }

    private fun seriesMissingFirst(): Condition {
        val m2 = BOOK_METADATA.`as`("smf_m")
        return notExists(
            selectOne().from(m2)
                .where(m2.SERIES_NAME.eq(bm.SERIES_NAME))
                .and(floor(m2.SERIES_NUMBER).eq(inline(1.0)))
        )
    }

    private fun seriesHasDuplicateNumber(): Condition {
        val mT = BOOK_METADATA.`as`("dup_t")
        val mD = BOOK_METADATA.`as`("dup_d")
        val total = select(count())
            .from(mT)
            .where(mT.SERIES_NAME.eq(bm.SERIES_NAME))
            .and(mT.SERIES_NUMBER.isNotNull)
        val distinct = select(countDistinct(mD.SERIES_NUMBER))
            .from(mD)
            .where(mD.SERIES_NAME.eq(bm.SERIES_NAME))
            .and(mD.SERIES_NUMBER.isNotNull)
        return field(total).gt(field(distinct))
    }

    private fun seriesNumberBound(agg: (Field<Double>) -> org.jooq.AggregateFunction<Double>): Field<Double> {
        val m2 = BOOK_METADATA.`as`("snb_m")
        return field(
            select(agg(m2.SERIES_NUMBER))
                .from(m2)
                .where(m2.SERIES_NAME.eq(bm.SERIES_NAME))
                .and(m2.SERIES_NUMBER.isNotNull)
        )
    }

    private fun isNextUnread(userId: Long?): Condition {
        val notRead = ubp.READ_STATUS.isNull.or(ubp.READ_STATUS.ne("READ"))

        val mLu = BOOK_METADATA.`as`("nu_lm")
        val pLu = USER_BOOK_PROGRESS.`as`("nu_lp")
        val noLowerUnread = notExists(
            selectOne().from(mLu)
                .leftJoin(pLu).on(pLu.BOOK_ID.eq(mLu.BOOK_ID))
                .where(mLu.SERIES_NAME.eq(bm.SERIES_NAME))
                .and(mLu.SERIES_NUMBER.isNotNull)
                .and(mLu.SERIES_NUMBER.lt(bm.SERIES_NUMBER))
                .and(pLu.READ_STATUS.isNull.or(pLu.READ_STATUS.ne("READ")))
                .and(pLu.USER_ID.isNull.or(if (userId != null) pLu.USER_ID.eq(userId) else falseCondition()))
        )

        val mPr = BOOK_METADATA.`as`("nu_pm")
        val pPr = USER_BOOK_PROGRESS.`as`("nu_pp")
        val hasPriorRead = exists(
            selectOne().from(mPr)
                .join(pPr).on(pPr.BOOK_ID.eq(mPr.BOOK_ID))
                .where(mPr.SERIES_NAME.eq(bm.SERIES_NAME))
                .and(mPr.SERIES_NUMBER.isNotNull)
                .and(mPr.SERIES_NUMBER.lt(bm.SERIES_NUMBER))
                .and(if (userId != null) pPr.USER_ID.eq(userId) else falseCondition())
                .and(pPr.READ_STATUS.eq("READ"))
        )

        return notRead.and(noLowerUnread).and(hasPriorRead)
    }

    // ========================================================================
    // Array fields (authors, categories, moods, tags, genre, shelf)
    // ========================================================================

    private fun isArrayField(field: RuleField): Boolean =
        field == RuleField.AUTHORS || field == RuleField.CATEGORIES ||
                field == RuleField.MOODS || field == RuleField.TAGS ||
                field == RuleField.GENRE || field == RuleField.SHELF

    private fun arrayFieldCondition(field: RuleField, values: List<String>, includesAll: Boolean): Condition {
        if (values.isEmpty()) return noCondition()
        return if (includesAll) {
            and(values.map { value -> arrayFieldMatch(field) { name -> lower(name).eq(value.lowercase()) } })
        } else {
            arrayFieldIn(field, values)
        }
    }

    private fun arrayFieldIn(field: RuleField, values: List<String>): Condition {
        if (values.isEmpty()) return noCondition()
        val lowerValues = values.map { it.lowercase() }
        return arrayFieldMatch(field) { name -> lower(name).`in`(lowerValues) }
    }

    private fun arrayFieldMatch(field: RuleField, predicate: (Field<String>) -> Condition): Condition =
        when (field) {
            RuleField.AUTHORS -> {
                val m = BOOK_METADATA_AUTHOR_MAPPING.`as`("arr_am")
                val a = AUTHOR.`as`("arr_a")
                exists(
                    selectOne().from(m).join(a).on(a.ID.eq(m.AUTHOR_ID))
                        .where(m.BOOK_ID.eq(BOOK.ID)).and(predicate(a.NAME))
                )
            }
            RuleField.CATEGORIES, RuleField.GENRE -> {
                val m = BOOK_METADATA_CATEGORY_MAPPING.`as`("arr_cm")
                val c = CATEGORY.`as`("arr_c")
                exists(
                    selectOne().from(m).join(c).on(c.ID.eq(m.CATEGORY_ID))
                        .where(m.BOOK_ID.eq(BOOK.ID)).and(predicate(c.NAME))
                )
            }
            RuleField.MOODS -> {
                val m = BOOK_METADATA_MOOD_MAPPING.`as`("arr_mm")
                val mo = MOOD.`as`("arr_mo")
                exists(
                    selectOne().from(m).join(mo).on(mo.ID.eq(m.MOOD_ID))
                        .where(m.BOOK_ID.eq(BOOK.ID)).and(predicate(mo.NAME))
                )
            }
            RuleField.TAGS -> {
                val m = BOOK_METADATA_TAG_MAPPING.`as`("arr_tm")
                val t = TAG.`as`("arr_t")
                exists(
                    selectOne().from(m).join(t).on(t.ID.eq(m.TAG_ID))
                        .where(m.BOOK_ID.eq(BOOK.ID)).and(predicate(t.NAME))
                )
            }
            RuleField.SHELF -> {
                val s = BOOK_SHELF_MAPPING.`as`("arr_s")
                exists(
                    selectOne().from(s)
                        .where(s.BOOK_ID.eq(BOOK.ID))
                        .and(predicate(s.SHELF_ID.cast(SQLDataType.VARCHAR)))
                )
            }
            else -> noCondition()
        }

    private fun arrayFieldHasAny(field: RuleField): Condition = when (field) {
        RuleField.AUTHORS -> mappingExists(BOOK_METADATA_AUTHOR_MAPPING, BOOK_METADATA_AUTHOR_MAPPING.BOOK_ID)
        RuleField.CATEGORIES, RuleField.GENRE ->
            mappingExists(BOOK_METADATA_CATEGORY_MAPPING, BOOK_METADATA_CATEGORY_MAPPING.BOOK_ID)
        RuleField.MOODS -> mappingExists(BOOK_METADATA_MOOD_MAPPING, BOOK_METADATA_MOOD_MAPPING.BOOK_ID)
        RuleField.TAGS -> mappingExists(BOOK_METADATA_TAG_MAPPING, BOOK_METADATA_TAG_MAPPING.BOOK_ID)
        RuleField.SHELF -> mappingExists(BOOK_SHELF_MAPPING, BOOK_SHELF_MAPPING.BOOK_ID)
        else -> noCondition()
    }

    // ========================================================================
    // Field mapping and value normalization
    // ========================================================================

    private fun fieldFor(field: RuleField): Field<*>? = when (field) {
        RuleField.LIBRARY -> BOOK.LIBRARY_ID
        RuleField.SHELF -> null
        RuleField.READ_STATUS -> ubp.READ_STATUS
        RuleField.DATE_FINISHED -> ubp.DATE_FINISHED
        RuleField.LAST_READ_TIME -> ubp.LAST_READ_TIME
        RuleField.PERSONAL_RATING -> ubp.PERSONAL_RATING
        RuleField.FILE_SIZE -> bf.FILE_SIZE_KB
        RuleField.METADATA_SCORE -> BOOK.METADATA_MATCH_SCORE
        RuleField.TITLE -> bm.TITLE
        RuleField.SUBTITLE -> bm.SUBTITLE
        RuleField.PUBLISHER -> bm.PUBLISHER
        RuleField.PUBLISHED_DATE -> bm.PUBLISHED_DATE
        RuleField.PAGE_COUNT -> bm.PAGE_COUNT
        RuleField.LANGUAGE -> bm.LANGUAGE
        RuleField.SERIES_NAME -> bm.SERIES_NAME
        RuleField.SERIES_NUMBER -> bm.SERIES_NUMBER
        RuleField.SERIES_TOTAL -> bm.SERIES_TOTAL
        RuleField.ISBN13 -> bm.ISBN_13
        RuleField.ISBN10 -> bm.ISBN_10
        RuleField.AMAZON_RATING -> bm.AMAZON_RATING
        RuleField.AMAZON_REVIEW_COUNT -> bm.AMAZON_REVIEW_COUNT
        RuleField.GOODREADS_RATING -> bm.GOODREADS_RATING
        RuleField.GOODREADS_REVIEW_COUNT -> bm.GOODREADS_REVIEW_COUNT
        RuleField.HARDCOVER_RATING -> bm.HARDCOVER_RATING
        RuleField.HARDCOVER_REVIEW_COUNT -> bm.HARDCOVER_REVIEW_COUNT
        RuleField.RANOBEDB_RATING -> bm.RANOBEDB_RATING
        RuleField.AGE_RATING -> bm.AGE_RATING
        RuleField.CONTENT_RATING -> bm.CONTENT_RATING
        RuleField.ADDED_ON -> BOOK.ADDED_ON
        RuleField.LUBIMYCZYTAC_RATING -> bm.LUBIMYCZYTAC_RATING
        RuleField.DESCRIPTION -> bm.DESCRIPTION
        RuleField.NARRATOR -> bm.NARRATOR
        RuleField.AUDIBLE_RATING -> bm.AUDIBLE_RATING
        RuleField.AUDIBLE_REVIEW_COUNT -> bm.AUDIBLE_REVIEW_COUNT
        RuleField.ABRIDGED -> bm.ABRIDGED
        RuleField.AUDIOBOOK_DURATION -> bf.DURATION_SECONDS
        RuleField.AUDIOBOOK_CODEC -> bf.CODEC
        RuleField.AUDIOBOOK_CHAPTER_COUNT -> bf.CHAPTER_COUNT
        RuleField.AUDIOBOOK_BITRATE -> bf.BITRATE
        RuleField.IS_PHYSICAL -> BOOK.IS_PHYSICAL
        RuleField.READING_PROGRESS -> greatest(
            coalesce(ubp.KOREADER_PROGRESS_PERCENT, inline(0.0)),
            coalesce(ubp.KOBO_PROGRESS_PERCENT, inline(0.0)),
            coalesce(ubp.PDF_PROGRESS_PERCENT, inline(0.0)),
            coalesce(ubp.EPUB_PROGRESS_PERCENT, inline(0.0)),
            coalesce(ubp.CBX_PROGRESS_PERCENT, inline(0.0))
        )
        RuleField.FILE_TYPE -> function(
            "substring_index", SQLDataType.VARCHAR, bf.FILE_NAME, inline("."), inline(-1)
        )
        else -> null
    }

    private fun booleanField(f: Field<*>): Field<Boolean> = f.coerce(SQLDataType.BOOLEAN)

    private fun normalizeValue(value: Any?, field: RuleField): Any? {
        if (value == null) return null

        if (field == RuleField.PUBLISHED_DATE) return parseDate(value)?.toLocalDate()

        if (field == RuleField.DATE_FINISHED || field == RuleField.LAST_READ_TIME || field == RuleField.ADDED_ON) {
            return parseDate(value)
        }

        if (field == RuleField.READ_STATUS) return value.toString()

        if (field == RuleField.ABRIDGED || field == RuleField.IS_PHYSICAL) {
            return value.toString().toBoolean()
        }

        if (value is Number) return value

        if (field in NUMERIC_FIELDS) {
            value.toString().toDoubleOrNull()?.let { return it }
        }

        return value.toString().lowercase()
    }

    private fun parseDate(value: Any?): LocalDateTime? {
        if (value == null) return null
        if (value is LocalDateTime) return value
        return try {
            LocalDateTime.parse(value.toString(), DateTimeFormatter.ISO_DATE_TIME)
        } catch (_: Exception) {
            try {
                LocalDate.parse(value.toString()).atStartOfDay()
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun toStringList(value: Any?): List<String> = when (value) {
        null -> emptyList()
        is List<*> -> value.map { it.toString() }
        else -> listOf(value.toString())
    }

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    companion object {
        private val COMPOSITE_FIELDS = setOf(
            RuleField.SERIES_STATUS, RuleField.SERIES_GAPS, RuleField.SERIES_POSITION
        )

        private val NUMERIC_FIELDS = setOf(
            RuleField.METADATA_SCORE, RuleField.FILE_SIZE, RuleField.PAGE_COUNT,
            RuleField.SERIES_NUMBER, RuleField.SERIES_TOTAL, RuleField.AGE_RATING,
            RuleField.PERSONAL_RATING, RuleField.READING_PROGRESS, RuleField.AUDIOBOOK_DURATION,
            RuleField.AMAZON_RATING, RuleField.AMAZON_REVIEW_COUNT,
            RuleField.GOODREADS_RATING, RuleField.GOODREADS_REVIEW_COUNT,
            RuleField.HARDCOVER_RATING, RuleField.HARDCOVER_REVIEW_COUNT,
            RuleField.LUBIMYCZYTAC_RATING, RuleField.RANOBEDB_RATING,
            RuleField.AUDIBLE_RATING, RuleField.AUDIBLE_REVIEW_COUNT,
            RuleField.AUDIOBOOK_CHAPTER_COUNT, RuleField.AUDIOBOOK_BITRATE
        )

        private val METADATA_STRING_COLUMNS: Map<String, Field<String>> = mapOf(
            "title" to BOOK_METADATA.TITLE,
            "subtitle" to BOOK_METADATA.SUBTITLE,
            "description" to BOOK_METADATA.DESCRIPTION,
            "publisher" to BOOK_METADATA.PUBLISHER,
            "language" to BOOK_METADATA.LANGUAGE,
            "seriesName" to BOOK_METADATA.SERIES_NAME,
            "isbn13" to BOOK_METADATA.ISBN_13,
            "isbn10" to BOOK_METADATA.ISBN_10,
            "asin" to BOOK_METADATA.ASIN,
            "contentRating" to BOOK_METADATA.CONTENT_RATING,
            "narrator" to BOOK_METADATA.NARRATOR,
            "goodreadsId" to BOOK_METADATA.GOODREADS_ID,
            "hardcoverId" to BOOK_METADATA.HARDCOVER_ID,
            "googleId" to BOOK_METADATA.GOOGLE_ID,
            "audibleId" to BOOK_METADATA.AUDIBLE_ID,
            "lubimyczytacId" to BOOK_METADATA.LUBIMYCZYTAC_ID,
            "ranobedbId" to BOOK_METADATA.RANOBEDB_ID,
            "comicvineId" to BOOK_METADATA.COMICVINE_ID
        )

        private val METADATA_NULLABLE_COLUMNS: Map<String, Field<*>> = mapOf(
            "pageCount" to BOOK_METADATA.PAGE_COUNT,
            "seriesNumber" to BOOK_METADATA.SERIES_NUMBER,
            "seriesTotal" to BOOK_METADATA.SERIES_TOTAL,
            "ageRating" to BOOK_METADATA.AGE_RATING,
            "publishedDate" to BOOK_METADATA.PUBLISHED_DATE,
            "abridged" to BOOK_METADATA.ABRIDGED,
            "amazonRating" to BOOK_METADATA.AMAZON_RATING,
            "goodreadsRating" to BOOK_METADATA.GOODREADS_RATING,
            "hardcoverRating" to BOOK_METADATA.HARDCOVER_RATING,
            "ranobedbRating" to BOOK_METADATA.RANOBEDB_RATING,
            "lubimyczytacRating" to BOOK_METADATA.LUBIMYCZYTAC_RATING,
            "audibleRating" to BOOK_METADATA.AUDIBLE_RATING,
            "amazonReviewCount" to BOOK_METADATA.AMAZON_REVIEW_COUNT,
            "goodreadsReviewCount" to BOOK_METADATA.GOODREADS_REVIEW_COUNT,
            "hardcoverReviewCount" to BOOK_METADATA.HARDCOVER_REVIEW_COUNT,
            "audibleReviewCount" to BOOK_METADATA.AUDIBLE_REVIEW_COUNT
        )
    }
}

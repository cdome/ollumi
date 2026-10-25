package org.booklore.repository.jooq.dto

import org.booklore.model.enums.ReadStatus
import java.time.Instant

/**
 * Mutable domain row replacing the JPA UserBookProgressEntity. Field types mirror the
 * old entity (Instant / Float / Int / ReadStatus) so consumers keep the same getters/setters;
 * the jOOQ repository handles the column conversions (Float<->Double, Int<->Byte,
 * Instant<->LocalDateTime UTC, ReadStatus<->String). The @ManyToOne user/book relations are
 * replaced by plain userId/bookId columns. `@JvmOverloads` gives Java a no-arg constructor
 * (`new UserBookProgressRow()`), matching the find-or-new + set-a-subset + save() write style.
 */
data class UserBookProgressRow @JvmOverloads constructor(
    var id: Long? = null,
    var userId: Long? = null,
    var bookId: Long? = null,
    var lastReadTime: Instant? = null,
    var pdfProgress: Int? = null,
    var pdfProgressPercent: Float? = null,
    var epubProgress: String? = null,
    var epubProgressHref: String? = null,
    var epubProgressPercent: Float? = null,
    var cbxProgress: Int? = null,
    var cbxProgressPercent: Float? = null,
    var koreaderProgress: String? = null,
    var koreaderProgressPercent: Float? = null,
    var koreaderDevice: String? = null,
    var koreaderDeviceId: String? = null,
    var koboProgressPercent: Float? = null,
    var koboLocation: String? = null,
    var koboLocationType: String? = null,
    var koboLocationSource: String? = null,
    var readStatus: ReadStatus? = null,
    var dateFinished: Instant? = null,
    var koreaderLastSyncTime: Instant? = null,
    var koboProgressReceivedTime: Instant? = null,
    var koboStatusSentTime: Instant? = null,
    var koboProgressSentTime: Instant? = null,
    var readStatusModifiedTime: Instant? = null,
    var personalRating: Int? = null,
)

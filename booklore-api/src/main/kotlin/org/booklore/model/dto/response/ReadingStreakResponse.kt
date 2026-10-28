package org.booklore.model.dto.response

import java.time.LocalDate
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class ReadingStreakResponse constructor(
        val currentStreak: Int? = null,
        val longestStreak: Int? = null,
        val totalReadingDays: Int? = null,
        val last52Weeks: List<ReadingStreakDay?>? = null
) {
    data class ReadingStreakDay constructor(
            val date: LocalDate? = null,
            @get:JvmName("isActive")
            @get:JsonProperty("active")
            val active: Boolean? = null
    )
}

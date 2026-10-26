package org.booklore.model.dto.response.ranobedbapi

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonCreator

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RanobedbBookResponse constructor(
        val book: Book? = null
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Book constructor(
            val description: String? = null,
            val lang: String? = null,
            val id: Long? = null,
            val romaji: String? = null,
            @JsonProperty("description_ja")
            val descriptionJa: String? = null,
            val hidden: Boolean? = null,
            @JsonProperty("image_id")
            val imageId: Long? = null,
            val olang: String? = null,
            val locked: Boolean? = null,
            @JsonProperty("c_release_date")
            val cReleaseDate: Long? = null,
            val title: String? = null,
            @JsonProperty("title_orig")
            val titleOrig: String? = null,
            @JsonProperty("romaji_orig")
            val romajiOrig: String? = null,
            val image: Image? = null,
            val rating: Rating? = null,
            val titles: List<TitleEntry?>? = null,
            val editions: List<Edition?>? = null,
            val releases: List<Release?>? = null,
            val publishers: List<Publisher?>? = null,
            val series: Series? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Rating constructor(
            val score: Double? = null,
            val count: Int? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Image constructor(
            val id: Long? = null,
            val filename: String? = null,
            val height: Int? = null,
            val nsfw: Boolean? = null,
            val spoiler: Boolean? = null,
            val width: Int? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TitleEntry constructor(
            val lang: String? = null,
            val romaji: String? = null,
            @JsonProperty("book_id")
            val bookId: Long? = null,
            val official: Boolean? = null,
            val title: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Edition constructor(
            @JsonProperty("book_id")
            val bookId: Long? = null,
            val lang: String? = null,
            val title: String? = null,
            val eid: Long? = null,
            val staff: List<Staff?>? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Staff constructor(
            val note: String? = null,
            @JsonProperty("role_type")
            val roleType: RoleType? = null,
            val romaji: String? = null,
            val name: String? = null,
            @JsonProperty("staff_id")
            val staffId: Long? = null,
            @JsonProperty("staff_alias_id")
            val staffAliasId: Long? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Release constructor(
            val lang: String? = null,
            val id: Long? = null,
            val romaji: String? = null,
            val description: String? = null,
            val hidden: Boolean? = null,
            val locked: Boolean? = null,
            @JsonProperty("release_date")
            val releaseDate: Long? = null,
            val title: String? = null,
            val website: String? = null,
            val amazon: String? = null,
            val bookwalker: String? = null,
            val format: Format? = null,
            val isbn13: String? = null,
            val pages: Int? = null,
            val rakuten: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Publisher constructor(
            val lang: String? = null,
            val id: Long? = null,
            val romaji: String? = null,
            val name: String? = null,
            @JsonProperty("publisher_type")
            val publisherType: PublisherType? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Series constructor(
            val books: List<SeriesBook?>? = null,
            val tags: List<Tag?>? = null,
            val lang: String? = null,
            val id: Long? = null,
            val romaji: String? = null,
            val title: String? = null,
            @JsonProperty("title_orig")
            val titleOrig: String? = null,
            @JsonProperty("romaji_orig")
            val romajiOrig: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SeriesBook constructor(
            val id: Long? = null,
            val lang: String? = null,
            val romaji: String? = null,
            val title: String? = null,
            @JsonProperty("title_orig")
            val titleOrig: String? = null,
            @JsonProperty("romaji_orig")
            val romajiOrig: String? = null,
            val image: Image? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Tag constructor(
            val id: Long? = null,
            val name: String? = null,
            val ttype: TagType? = null
    )

    enum class RoleType {
        @JsonProperty("editor") EDITOR,
        @JsonProperty("staff") STAFF,
        @JsonProperty("author") AUTHOR,
        @JsonProperty("artist") ARTIST,
        @JsonProperty("translator") TRANSLATOR,
        @JsonProperty("narrator") NARRATOR
    }

    enum class Format {
        @JsonProperty("digital") DIGITAL,
        @JsonProperty("print") PRINT,
        @JsonProperty("audio") AUDIO
    }

    enum class PublisherType {
        @JsonProperty("publisher") PUBLISHER,
        @JsonProperty("imprint") IMPRINT
    }

    enum class TagType {
        @JsonProperty("tag") TAG,
        @JsonProperty("content") CONTENT,
        @JsonProperty("demographic") DEMOGRAPHIC,
        @JsonProperty("genre") GENRE
    }
}

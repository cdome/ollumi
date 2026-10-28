package org.booklore.model.dto.response.comicvineapi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonCreator

@JsonIgnoreProperties(ignoreUnknown = true)
data class Comic constructor(
        val id: Int? = null,
        @JsonProperty("api_detail_url")
        val apiDetailUrl: String? = null,
        @JsonProperty("cover_date")
        val coverDate: String? = null,
        @JsonProperty("store_date")
        val storeDate: String? = null,
        val description: String? = null,
        val deck: String? = null,
        val name: String? = null,
        val aliases: String? = null,
        @JsonProperty("issue_number")
        val issueNumber: String? = null,
        val image: Image? = null,
        val volume: Volume? = null,
        @JsonProperty("resource_type")
        val resourceType: String? = null,
        @JsonProperty("person_credits")
        val personCredits: List<PersonCredit?>? = null,
        @JsonProperty("character_credits")
        val characterCredits: List<CharacterCredit?>? = null,
        @JsonProperty("team_credits")
        val teamCredits: List<TeamCredit?>? = null,
        @JsonProperty("story_arc_credits")
        val storyArcCredits: List<StoryArcCredit?>? = null,
        @JsonProperty("location_credits")
        val locationCredits: List<LocationCredit?>? = null,
        @JsonProperty("start_year")
        val startYear: String? = null,
        @JsonProperty("count_of_issues")
        val countOfIssues: Int? = null,
        @JsonProperty("site_detail_url")
        val siteDetailUrl: String? = null,
        @JsonProperty("first_issue")
        val firstIssue: FirstLastIssue? = null,
        @JsonProperty("last_issue")
        val lastIssue: FirstLastIssue? = null,
        val publisher: Publisher? = null
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Publisher constructor(
            val id: Int? = null,
            val name: String? = null,
            @JsonProperty("api_detail_url")
            val apiDetailUrl: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Image constructor(
            @JsonProperty("icon_url")
            val iconUrl: String? = null,
            @JsonProperty("medium_url")
            val mediumUrl: String? = null,
            @JsonProperty("screen_url")
            val screenUrl: String? = null,
            @JsonProperty("screen_large_url")
            val screenLargeUrl: String? = null,
            @JsonProperty("small_url")
            val smallUrl: String? = null,
            @JsonProperty("super_url")
            val superUrl: String? = null,
            @JsonProperty("thumb_url")
            val thumbUrl: String? = null,
            @JsonProperty("tiny_url")
            val tinyUrl: String? = null,
            @JsonProperty("original_url")
            val originalUrl: String? = null,
            @JsonProperty("image_tags")
            val imageTags: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Volume constructor(
            val id: Int? = null,
            val name: String? = null,
            @JsonProperty("api_detail_url")
            val apiDetailUrl: String? = null,
            @JsonProperty("site_detail_url")
            val siteDetailUrl: String? = null,
            @JsonProperty("start_year")
            val startYear: String? = null,
            @JsonProperty("count_of_issues")
            val countOfIssues: Int? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PersonCredit constructor(
            val id: Long? = null,
            val name: String? = null,
            val role: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CharacterCredit constructor(
            val id: Long? = null,
            val name: String? = null,
            @JsonProperty("api_detail_url")
            val apiDetailUrl: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TeamCredit constructor(
            val id: Long? = null,
            val name: String? = null,
            @JsonProperty("api_detail_url")
            val apiDetailUrl: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class StoryArcCredit constructor(
            val id: Long? = null,
            val name: String? = null,
            @JsonProperty("api_detail_url")
            val apiDetailUrl: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class LocationCredit constructor(
            val id: Long? = null,
            val name: String? = null,
            @JsonProperty("api_detail_url")
            val apiDetailUrl: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class FirstLastIssue constructor(
            val id: Int? = null,
            val name: String? = null,
            @JsonProperty("api_detail_url")
            val apiDetailUrl: String? = null,
            @JsonProperty("issue_number")
            val issueNumber: String? = null
    )
}

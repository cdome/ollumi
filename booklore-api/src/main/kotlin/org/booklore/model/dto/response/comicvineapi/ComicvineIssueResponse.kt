package org.booklore.model.dto.response.comicvineapi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonCreator

@JsonIgnoreProperties(ignoreUnknown = true)
data class ComicvineIssueResponse constructor(
        val error: String? = null,
        val limit: Int? = null,
        val offset: Int? = null,
        val number_of_page_results: Int? = null,
        val number_of_total_results: Int? = null,
        val status_code: Int? = null,
        val results: IssueResults? = null,
        val version: String? = null
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class IssueResults constructor(
            @JsonProperty("person_credits")
            val personCredits: List<Comic.PersonCredit?>? = null,
            @JsonProperty("character_credits")
            val characterCredits: List<Comic.CharacterCredit?>? = null,
            @JsonProperty("team_credits")
            val teamCredits: List<Comic.TeamCredit?>? = null,
            @JsonProperty("story_arc_credits")
            val storyArcCredits: List<Comic.StoryArcCredit?>? = null,
            @JsonProperty("location_credits")
            val locationCredits: List<Comic.LocationCredit?>? = null,
            val description: String? = null,
            val deck: String? = null,
            val name: String? = null,
            val aliases: String? = null,
            @JsonProperty("issue_number")
            val issueNumber: String? = null,
            @JsonProperty("cover_date")
            val coverDate: String? = null,
            @JsonProperty("store_date")
            val storeDate: String? = null,
            val image: Comic.Image? = null,
            val volume: Comic.Volume? = null,
            @JsonProperty("api_detail_url")
            val apiDetailUrl: String? = null,
            @JsonProperty("site_detail_url")
            val siteDetailUrl: String? = null
    )
}

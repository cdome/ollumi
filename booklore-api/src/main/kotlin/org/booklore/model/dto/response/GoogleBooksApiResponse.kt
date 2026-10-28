package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonCreator

@JsonIgnoreProperties(ignoreUnknown = true)
data class GoogleBooksApiResponse constructor(
        val totalItems: Int? = null,
        val items: List<Item?>? = null
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Item constructor(
            val id: String? = null,
            val selfLink: String? = null,
            val volumeInfo: VolumeInfo? = null
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class VolumeInfo constructor(
                val title: String? = null,
                val subtitle: String? = null,
                val authors: List<String?>? = null,
                val publisher: String? = null,
                val publishedDate: String? = null,
                val description: String? = null,
                val industryIdentifiers: List<IndustryIdentifier?>? = null,
                val pageCount: Int? = null,
                val printedPageCount: Int? = null,
                val imageLinks: ImageLinks? = null,
                val language: String? = null,
                val categories: Set<String?>? = null,
                val averageRating: Double? = null,
                val ratingsCount: Int? = null,
                val maturityRating: String? = null,
                val printType: String? = null,
                val previewLink: String? = null,
                val infoLink: String? = null,
                val canonicalVolumeLink: String? = null,
                val seriesInfo: SeriesInfo? = null
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class IndustryIdentifier constructor(
                val type: String? = null,
                val identifier: String? = null
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class ImageLinks constructor(
                val smallThumbnail: String? = null,
                val thumbnail: String? = null,
                val small: String? = null,
                val medium: String? = null,
                val large: String? = null,
                val extraLarge: String? = null
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class SeriesInfo constructor(
                val kind: String? = null,
                val shortSeriesBookTitle: String? = null,
                val bookDisplayNumber: String? = null,
                val volumeSeries: List<VolumeSeries?>? = null
        ) {
            @JsonIgnoreProperties(ignoreUnknown = true)
            data class VolumeSeries constructor(
                    val seriesId: String? = null,
                    val seriesBookType: String? = null,
                    val orderNumber: Int? = null
            )
        }
    }
}

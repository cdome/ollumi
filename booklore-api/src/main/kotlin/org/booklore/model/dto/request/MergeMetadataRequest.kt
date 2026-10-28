package org.booklore.model.dto.request

import org.booklore.model.enums.MergeMetadataType

data class MergeMetadataRequest(
    val metadataType: MergeMetadataType? = null,
    val targetValues: List<String>? = null,
    val valuesToMerge: List<String>? = null
)

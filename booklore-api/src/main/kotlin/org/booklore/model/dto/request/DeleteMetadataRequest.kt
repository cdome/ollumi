package org.booklore.model.dto.request

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import org.booklore.model.enums.MergeMetadataType

data class DeleteMetadataRequest(
    @field:NotNull
    val metadataType: MergeMetadataType? = null,

    @field:NotEmpty
    val valuesToDelete: List<String>? = null
)

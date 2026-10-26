package org.booklore.model.dto.response

import org.booklore.model.dto.MetadataFetchTask
import com.fasterxml.jackson.annotation.JsonCreator

data class MetadataTaskDetailsResponse constructor(
        val task: MetadataFetchTask? = null
)

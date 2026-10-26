package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator

data class ListeningFinishFunnelResponse constructor(
        val totalStarted: Long? = null,
        val reached25: Long? = null,
        val reached50: Long? = null,
        val reached75: Long? = null,
        val completed: Long? = null
)

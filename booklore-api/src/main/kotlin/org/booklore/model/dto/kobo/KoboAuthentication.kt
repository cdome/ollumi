package org.booklore.model.dto.kobo

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy::class)
data class KoboAuthentication(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenType: String? = "Bearer",
    val trackingId: String? = null,
    val userKey: String? = null
)

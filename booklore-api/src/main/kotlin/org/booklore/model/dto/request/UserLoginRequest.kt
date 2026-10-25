package org.booklore.model.dto.request

import jakarta.validation.constraints.NotBlank

data class UserLoginRequest(
    @field:NotBlank(message = "Username must not be blank")
    val username: String? = null,

    @field:NotBlank(message = "Password must not be blank")
    val password: String? = null
)

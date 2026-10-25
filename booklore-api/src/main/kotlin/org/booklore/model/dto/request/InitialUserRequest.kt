package org.booklore.model.dto.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class InitialUserRequest(
    @field:NotBlank
    val username: String? = null,

    @field:Email
    @field:NotBlank
    val email: String? = null,

    @field:NotBlank
    val name: String? = null,

    @field:NotBlank
    @field:Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters long")
    val password: String? = null
)

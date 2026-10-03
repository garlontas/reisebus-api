package app.reisebus.reisebus_api.platform.api.contract

import jakarta.validation.constraints.NotBlank

data class CreateTenantRequest(
    @field:NotBlank val companyName: String,
    @field:NotBlank val desiredSlug: String,
)

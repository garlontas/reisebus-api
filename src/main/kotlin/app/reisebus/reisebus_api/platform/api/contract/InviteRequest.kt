package app.reisebus.reisebus_api.platform.api.contract

import app.reisebus.reisebus_api.common.context.InvitableRole
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class InviteRequest(@field:NotBlank @field:Email val email: String, val role: InvitableRole)

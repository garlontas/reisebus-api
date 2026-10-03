package app.reisebus.reisebus_api.platform.api.contract

import jakarta.validation.constraints.NotBlank

data class AcceptInvitationRequest(@field:NotBlank val token: String)

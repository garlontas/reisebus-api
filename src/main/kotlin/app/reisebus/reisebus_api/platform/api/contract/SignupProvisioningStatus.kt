package app.reisebus.reisebus_api.platform.api.contract

import java.util.*

data class SignupProvisioningStatus(val id: UUID, val status: TenantCreationStatus, val shopSlug: String, val error: String?)

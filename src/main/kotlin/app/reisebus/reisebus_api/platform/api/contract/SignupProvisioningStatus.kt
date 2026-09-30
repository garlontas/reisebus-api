package app.reisebus.reisebus_api.platform.api.contract

import java.util.UUID

data class SignupProvisioningStatus(val id: UUID, val status: TenantCreationStatus, val shopSlug: String, val error: String?)

enum class TenantCreationStatus {
    PROVISIONING,
    ACTIVE,
    SUSPENDED,
    DELETED,
    AWAITING_PAYMENT,
    FAILED
}

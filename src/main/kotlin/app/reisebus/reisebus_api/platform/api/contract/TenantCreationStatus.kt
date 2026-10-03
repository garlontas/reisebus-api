package app.reisebus.reisebus_api.platform.api.contract

enum class TenantCreationStatus {
    PROVISIONING,
    ACTIVE,
    SUSPENDED,
    DELETED,
    AWAITING_PAYMENT,
    FAILED
}

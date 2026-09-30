package app.reisebus.reisebus_api.platform.messaging

import app.reisebus.reisebus_api.platform.domain.TenantStatus
import java.time.Instant
import java.util.UUID

sealed interface TenantMessage {
    val messageId: UUID
    val occurredAt: Instant
}

data class CreateTenant(
    val companyName: String,
    val shopSlug: String,
    override val messageId: UUID = UUID.randomUUID(),
    override val occurredAt: Instant = Instant.now(),
) : TenantMessage

data class TenantCreated(
    val tenantId: UUID,
    val companyName: String,
    val shopSlug: String,
    val schemaName: String,
    val status: TenantStatus,
    override val messageId: UUID = UUID.randomUUID(),
    override val occurredAt: Instant = Instant.now()
) : TenantMessage

data class TenantProvisioned(
    val tenantId: UUID,
    val companyName: String,
    val shopSlug: String,
    val schemaName: String,
    val status: TenantStatus,
    override val messageId: UUID = UUID.randomUUID(),
    override val occurredAt: Instant = Instant.now()
) : TenantMessage


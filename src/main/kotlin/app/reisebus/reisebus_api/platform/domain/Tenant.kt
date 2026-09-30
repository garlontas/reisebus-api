package app.reisebus.reisebus_api.platform.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "tenant", schema = "platform")
class Tenant(
    @Id
    val id: UUID,

    @Column(name = "company_name", nullable = false, length = 200)
    val companyName: String,

    @Column(nullable = false, unique = true, length = 63)
    val slug: String,

    @Column(name = "schema_name", nullable = false, unique = true, length = 63)
    val schemaName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: TenantStatus,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime
)

enum class TenantStatus {
    PROVISIONING,
    ACTIVE,
    SUSPENDED,
    DELETED,
    AWAITING_PAYMENT,
    FAILED
}

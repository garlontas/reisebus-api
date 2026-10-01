package app.reisebus.reisebus_api.platform.domain

import app.reisebus.reisebus_api.common.persistence.AuditedEntity
import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "tenant", schema = "platform")
class Tenant(
    @Id
    var id: UUID,

    @Column(name = "company_name", nullable = false, length = 200)
    var companyName: String,

    @Column(nullable = false, unique = true, length = 63)
    var slug: String,

    @Column(name = "schema_name", nullable = false, unique = true, length = 63)
    var schemaName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: TenantStatus,
) : AuditedEntity()

enum class TenantStatus {
    PROVISIONING,
    ACTIVE,
    SUSPENDED,
    DELETED,
    AWAITING_PAYMENT,
    FAILED
}

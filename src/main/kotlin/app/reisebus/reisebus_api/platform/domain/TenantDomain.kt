package app.reisebus.reisebus_api.platform.domain

import app.reisebus.reisebus_api.common.persistence.AuditedEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.*

@Entity
@Table(name = "tenant_domain", schema = "platform")
class TenantDomain(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    var id: UUID,

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    var tenant: Tenant,

    @Size(max = 255)
    @NotNull
    @Column(name = "domain", nullable = false)
    var domain: String = "",

    @NotNull
    @Column(name = "is_primary", nullable = false)
    var isPrimary: Boolean = false,

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: TenantDomainStatus = TenantDomainStatus.PENDING,

    @Column(name = "verified_at", nullable = true)
    var verifiedAt: OffsetDateTime? = null,

    @Column(name = "verification_token", nullable = true)
    var verificationToken: String? = null,
) : AuditedEntity()

enum class TenantDomainStatus {
    PENDING,
    ACTIVE,
    VERIFIED,
    FAILED,
}

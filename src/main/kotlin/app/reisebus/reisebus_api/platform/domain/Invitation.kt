package app.reisebus.reisebus_api.platform.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.*

@Entity
@Table(name = "invitation", schema = "platform")
class Invitation(
    @Id var id: UUID,
    var tenantId: UUID,
    var email: String,
    @Enumerated(EnumType.STRING)
    var role: UserTenantRole,
    var tokenHash: String,
    var expiresAt: Instant,
    var acceptedAt: Instant? = null,
    var createdAt: Instant = Instant.now(),
)

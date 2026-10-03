package app.reisebus.reisebus_api.platform.domain

import jakarta.persistence.*
import java.io.Serializable
import java.util.*

@Embeddable
data class UserTenantId(
    @Column(name = "user_id") val userId: UUID,
    @Column(name = "tenant_id") val tenantId: UUID,
) : Serializable

@Entity
@Table(name = "user_tenants", schema = "platform")
class UserTenant(
    @EmbeddedId var id: UserTenantId,

    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    var role: UserTenantRole,
)

enum class UserTenantRole {
    TENANT_ADMIN,
    STAFF,
    DRIVER
}

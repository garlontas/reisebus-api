package app.reisebus.reisebus_api.platform.service

import app.reisebus.reisebus_api.common.context.TenantInfo
import app.reisebus.reisebus_api.platform.persistence.TenantRepository
import app.reisebus.reisebus_api.platform.persistence.UserRepository
import app.reisebus.reisebus_api.platform.persistence.UserTenantRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

data class UserSummary(val id: UUID, val email: String, val firstName: String, val lastName: String)

data class TenantMembership(
    val tenantId: UUID,
    val companyName: String,
    val slug: String,
    val host: String,       // frontend redirects here after login
    val status: String,     // PROVISIONING | ACTIVE | ...
    val role: String,
)

data class MeResponse(
    val user: UserSummary,
    val platformAdmin: Boolean,
    val currentTenant: TenantMembership?,   // set when the request came in on a tenant host
    val tenants: List<TenantMembership>,    // for the tenant switcher
)

@Service
@Transactional(readOnly = true)
class MeService(
    private val users: UserRepository,
    private val memberships: UserTenantRepository,
    private val tenants: TenantRepository,
    @Value("\${app.base-domain}") private val baseDomain: String,
) {
    fun load(userId: UUID, current: TenantInfo?, platformAdmin: Boolean): MeResponse {
        val user = users.findById(userId).orElseThrow()
        val rows = memberships.findAllByIdUserId(userId)
        val tenantsById = tenants.findAllById(rows.map { it.id.tenantId }).associateBy { it.id }

        val list = rows.mapNotNull { m ->
            val t = tenantsById[m.id.tenantId] ?: return@mapNotNull null
            TenantMembership(t.id, t.companyName, t.slug, "${t.slug}.$baseDomain", t.status.name, m.role.name)
        }
        return MeResponse(
            user = UserSummary(user.id, user.email, user.firstName, user.lastName),
            platformAdmin = platformAdmin,
            currentTenant = list.firstOrNull { it.tenantId == current?.id },
            tenants = list,
        )
    }
}

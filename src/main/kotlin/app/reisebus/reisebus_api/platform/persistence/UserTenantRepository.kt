package app.reisebus.reisebus_api.platform.persistence

import app.reisebus.reisebus_api.platform.domain.UserTenant
import app.reisebus.reisebus_api.platform.domain.UserTenantId
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface UserTenantRepository : JpaRepository<UserTenant, UserTenantId> {
    fun findAllByIdUserId(userId: UUID): List<UserTenant>
    fun existsByIdUserIdAndIdTenantId(userId: UUID, tenantId: UUID): Boolean
    fun findByIdUserIdAndIdTenantId(userId: UUID, tenantId: UUID): UserTenant?
}

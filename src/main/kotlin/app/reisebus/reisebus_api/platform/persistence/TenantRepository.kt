package app.reisebus.reisebus_api.platform.persistence

import app.reisebus.reisebus_api.platform.domain.Tenant
import app.reisebus.reisebus_api.platform.domain.TenantStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TenantRepository : JpaRepository<Tenant, UUID> {
    fun findBySlug(slug: String): Tenant?
    fun findByStatusIn(statuses: Collection<TenantStatus>): List<Tenant>
}

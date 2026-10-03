package app.reisebus.reisebus_api.platform.persistence

import app.reisebus.reisebus_api.platform.domain.TenantDomain
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface TenantDomainRepository : JpaRepository<TenantDomain, UUID> {
    fun findActiveByDomain(domain: String): TenantDomain?
}

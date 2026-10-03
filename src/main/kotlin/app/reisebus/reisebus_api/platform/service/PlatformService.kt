package app.reisebus.reisebus_api.platform.service

import app.reisebus.reisebus_api.config.RESERVED_SLUGS
import app.reisebus.reisebus_api.platform.api.contract.SignupProvisioningStatus
import app.reisebus.reisebus_api.platform.api.contract.TenantCreationStatus
import app.reisebus.reisebus_api.platform.domain.Tenant
import app.reisebus.reisebus_api.platform.domain.TenantStatus
import app.reisebus.reisebus_api.platform.messaging.CreateTenant
import app.reisebus.reisebus_api.platform.messaging.TenantCreated
import app.reisebus.reisebus_api.platform.persistence.TenantRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class PlatformService(
    private val tenantRepository: TenantRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun createTenant(command: CreateTenant): TenantCreated {
        val companyName = command.companyName.trim()
        require(companyName.isNotEmpty()) { "Company name must not be blank" }

        val slug = command.shopSlug.trim().lowercase()
        require(slug.matches(SLUG_PATTERN)) {
            "Shop slug must contain only lowercase letters, numbers, and hyphens"
        }
        require(slug.length in 3..40) { "Shop slug must be 3-40 characters" }
        require(slug !in RESERVED_SLUGS) { "Shop slug is reserved" }

        if (tenantRepository.findBySlug(slug) != null) {
            throw DuplicateShopSlugException(slug)
        }

        val tenantId = UUID.randomUUID()
        val schemaName = "tenant_${slug}_${tenantId.toString().replace("-", "").take(12)}".replace("-", "_")
        val tenant = tenantRepository.save(
            Tenant(
                id = tenantId,
                companyName = companyName,
                slug = slug,
                schemaName = schemaName,
                status = TenantStatus.PROVISIONING,
            )
        )

        val event = TenantCreated(
            tenantId = tenant.id,
            companyName = companyName,
            shopSlug = tenant.slug,
            schemaName = tenant.schemaName,
            status = tenant.status
        )
        eventPublisher.publishEvent(event)
        return event
    }

    fun findBySlug(slug: String): Tenant? =
        tenantRepository.findBySlug(slug.trim().lowercase())

    fun getSignupProvisioningStatus(id: UUID): SignupProvisioningStatus {
        val tenant = tenantRepository.findById(id).orElseThrow { TenantNotFoundException(id) }
        return SignupProvisioningStatus(
            id = tenant.id,
            status = TenantCreationStatus.valueOf(tenant.status.name),
            shopSlug = tenant.slug,
            error = null
        )
    }

    fun isDesiredSlugAvailable(slug: String): Boolean {
        require(slug.trim().lowercase().matches(SLUG_PATTERN)) {
            "Shop slug must contain only lowercase letters, numbers, and hyphens"
        }
        return tenantRepository.findBySlug(slug.trim().lowercase()) == null
    }

    private companion object {
        val SLUG_PATTERN = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
    }
}

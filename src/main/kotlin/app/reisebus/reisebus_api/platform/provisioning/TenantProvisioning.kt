package app.reisebus.reisebus_api.platform.provisioning

import app.reisebus.reisebus_api.platform.application.TenantSchemaMigrator
import app.reisebus.reisebus_api.platform.domain.TenantStatus
import app.reisebus.reisebus_api.platform.messaging.TenantCreated
import app.reisebus.reisebus_api.platform.messaging.TenantProvisioned
import app.reisebus.reisebus_api.platform.persistence.TenantRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Service

@Service
internal class TenantProvisioning(
    private val tenantRepository: TenantRepository,
    private val tenantSchemaMigrator: TenantSchemaMigrator,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @ApplicationModuleListener
    fun on(event: TenantCreated) {
        logger.info("Starting tenant provisioning for tenant {} with schema {}", event.tenantId, event.schemaName)

        val tenant = tenantRepository.findById(event.tenantId).orElseThrow {
            IllegalStateException("Tenant ${event.tenantId} not found after creation")
        }

        try {
            logger.debug("Creating schema and running migrations for tenant {}", event.tenantId)
            tenantSchemaMigrator.migrateTenantSchema(event.schemaName)

            tenant.status = TenantStatus.ACTIVE
            tenantRepository.save(tenant)

            val provisionedEvent = TenantProvisioned(
                tenantId = event.tenantId,
                companyName = event.companyName,
                shopSlug = event.shopSlug,
                schemaName = event.schemaName,
                status = TenantStatus.ACTIVE
            )
            eventPublisher.publishEvent(provisionedEvent)

            logger.info("Tenant {} provisioned successfully", event.tenantId)
        } catch (e: Exception) {
            logger.error("Failed to provision tenant {} with schema {}", event.tenantId, event.schemaName, e)

            tenant.status = TenantStatus.FAILED
            tenantRepository.save(tenant)

            throw e
        }
    }
}

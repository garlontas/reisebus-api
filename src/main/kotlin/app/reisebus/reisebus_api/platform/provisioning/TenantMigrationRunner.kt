package app.reisebus.reisebus_api.platform.provisioning

import app.reisebus.reisebus_api.platform.application.TenantSchemaMigrator
import app.reisebus.reisebus_api.platform.domain.TenantStatus
import app.reisebus.reisebus_api.platform.persistence.TenantRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.OffsetDateTime

/**
 * Runs on application startup to migrate all ACTIVE or PROVISIONING tenants.
 * Failures are logged with the schema name and do not stop other tenants or the application.
 * Failed tenants are marked as FAILED.
 */
@Component
@ConditionalOnProperty(
    name = ["app.tenancy.migrate-on-startup"],
    havingValue = "true",
    matchIfMissing = false
)
internal class TenantMigrationRunner(
    private val tenantRepository: TenantRepository,
    private val tenantSchemaMigrator: TenantSchemaMigrator,
    private val clock: Clock
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val activeTenants = tenantRepository.findByStatusIn(listOf(TenantStatus.ACTIVE, TenantStatus.PROVISIONING))
        logger.info("Starting migration of {} active/provisioning tenant(s)", activeTenants.size)

        for (tenant in activeTenants) {
            try {
                logger.info("Migrating tenant: {} (schema: {})", tenant.slug, tenant.schemaName)
                tenantSchemaMigrator.migrateTenantSchema(tenant.schemaName)
                logger.info("Migration successful for tenant: {} (schema: {})", tenant.slug, tenant.schemaName)
            } catch (e: Exception) {
                logger.error("Migration failed for tenant: {} (schema: {}). Marking as FAILED.", tenant.slug, tenant.schemaName, e)
                tenant.status = TenantStatus.FAILED
                tenant.updatedAt = OffsetDateTime.now(clock)
                tenantRepository.save(tenant)
            }
        }

        val failedCount = activeTenants.count { it.status == TenantStatus.FAILED }
        if (failedCount > 0) {
            logger.warn("Startup complete, but {} tenant(s) failed to migrate and are marked FAILED", failedCount)
        } else {
            logger.info("All tenant migrations completed successfully")
        }
    }
}

package app.reisebus.reisebus_api.platform.provisioning

import app.reisebus.reisebus_api.platform.TestcontainersConfiguration
import app.reisebus.reisebus_api.platform.application.TenantSchemaMigrator
import app.reisebus.reisebus_api.platform.domain.Tenant
import app.reisebus.reisebus_api.platform.domain.TenantStatus
import app.reisebus.reisebus_api.platform.messaging.TenantCreated
import app.reisebus.reisebus_api.platform.persistence.TenantRepository
import com.ninjasquad.springmockk.MockkSpyBean
import io.mockk.every
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.util.*
import kotlin.test.assertEquals

/**
 * Real Postgres (Testcontainers), real transactions, real @ApplicationModuleListener (async, after commit).
 * The migrator is a spy: it runs the real Liquibase migration unless a test makes it fail.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class TenantProvisioningIT(
    @Autowired private val tenantRepository: TenantRepository,
    @Autowired private val eventPublisher: ApplicationEventPublisher,
    @Autowired private val transactionTemplate: TransactionTemplate,
    @Autowired private val jdbc: JdbcTemplate,
) {
    @MockkSpyBean
    private lateinit var migrator: TenantSchemaMigrator

    @Test
    fun `tenant becomes ACTIVE and its schema exists after the signup transaction commits`() {
        val tenant = createTenantAndPublishEvent("provision-ok")

        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            assertEquals(TenantStatus.ACTIVE, statusOf(tenant))
        }
        assertEquals(1, schemaCount(tenant.schemaName))
    }

    @Test
    fun `tenant is persisted as FAILED when the migration fails`() {
        every { migrator.migrateTenantSchema(match { it.contains("provision_fail") }) } throws
                IllegalStateException("boom")

        val tenant = createTenantAndPublishEvent("provision-fail")

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            assertEquals(TenantStatus.FAILED, statusOf(tenant))
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Same shape as signup: tenant row and event in ONE transaction; the listener runs after commit. */
    private fun createTenantAndPublishEvent(slug: String): Tenant {
        val id = UUID.randomUUID()
        val schema = "tenant_${slug}_${id.toString().replace("-", "").take(12)}".replace("-", "_")
        val tenant = Tenant(
            id = id,
            companyName = "IT $slug",
            slug = slug,
            schemaName = schema,
            status = TenantStatus.PROVISIONING,
        )
        transactionTemplate.executeWithoutResult {
            tenantRepository.save(tenant)
            eventPublisher.publishEvent(
                TenantCreated(
                    tenantId = id,
                    companyName = tenant.companyName,
                    shopSlug = slug,
                    schemaName = schema,
                    status = TenantStatus.PROVISIONING,
                )
            )
        }
        return tenant
    }

    private fun statusOf(tenant: Tenant) = tenantRepository.findById(tenant.id).orElseThrow().status

    private fun schemaCount(schema: String): Int? = jdbc.queryForObject(
        "SELECT count(*) FROM information_schema.schemata WHERE schema_name = ?",
        Int::class.java,
        schema,
    )
}
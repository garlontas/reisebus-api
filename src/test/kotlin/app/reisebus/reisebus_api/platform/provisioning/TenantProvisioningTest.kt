package app.reisebus.reisebus_api.platform.provisioning

import app.reisebus.reisebus_api.platform.application.TenantSchemaMigrator
import app.reisebus.reisebus_api.platform.domain.Tenant
import app.reisebus.reisebus_api.platform.domain.TenantStatus
import app.reisebus.reisebus_api.platform.messaging.TenantCreated
import app.reisebus.reisebus_api.platform.messaging.TenantProvisioned
import app.reisebus.reisebus_api.platform.persistence.TenantRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plain unit test: the listener logic only (order of steps, status changes, events, error handling).
 * Transaction and after-commit behaviour is NOT visible here; see TenantProvisioningIT for that.
 */
class TenantProvisioningTest {

    private val tenantRepository = mockk<TenantRepository>()
    private val migrator = mockk<TenantSchemaMigrator>()
    private val eventPublisher = mockk<ApplicationEventPublisher>()
    private val failureRecorder = mockk<TenantFailureRecorder>(relaxed = true)
    private val provisioning = TenantProvisioning(tenantRepository, migrator, eventPublisher, failureRecorder)

    private val tenantId = UUID.randomUUID()
    private val schemaName = "tenant_acme_0123456789ab"
    private val tenant = Tenant(
        id = tenantId,
        companyName = "Acme Tours",
        slug = "acme",
        schemaName = schemaName,
        status = TenantStatus.PROVISIONING,
    )
    private val event = TenantCreated(
        tenantId = tenantId,
        companyName = "Acme Tours",
        shopSlug = "acme",
        schemaName = schemaName,
        status = TenantStatus.PROVISIONING,
    )

    // The entity is mutated in place, so record the status at the moment of each save().
    private val savedStatuses = mutableListOf<TenantStatus>()
    private val published = mutableListOf<Any>()

    @BeforeEach
    fun setUp() {
        every { tenantRepository.findById(tenantId) } returns Optional.of(tenant)
        every { tenantRepository.save(any()) } answers { firstArg<Tenant>().also { savedStatuses += it.status } }
        every { eventPublisher.publishEvent(any<Any>()) } answers { published += firstArg<Any>() }
        every { migrator.migrateTenantSchema(any()) } just Runs
    }

    @Test
    fun `migrates the schema, then activates the tenant`() {
        provisioning.on(event)

        verifyOrder {
            migrator.migrateTenantSchema(schemaName)
            tenantRepository.save(tenant)
        }
        assertEquals(listOf(TenantStatus.ACTIVE), savedStatuses)
        assertEquals(TenantStatus.ACTIVE, tenant.status)
    }

    @Test
    fun `publishes TenantProvisioned with the tenant data after activation`() {
        provisioning.on(event)

        val provisioned = published.single() as TenantProvisioned
        assertEquals(tenantId, provisioned.tenantId)
        assertEquals("Acme Tours", provisioned.companyName)
        assertEquals("acme", provisioned.shopSlug)
        assertEquals(schemaName, provisioned.schemaName)
        assertEquals(TenantStatus.ACTIVE, provisioned.status)
    }

    @Test
    fun `records the failure, rethrows and publishes nothing when the migration fails`() {
        every { migrator.migrateTenantSchema(schemaName) } throws IllegalStateException("boom")

        val exception = assertThrows<IllegalStateException> { provisioning.on(event) }

        assertEquals("boom", exception.message)
        verify(exactly = 1) { failureRecorder.markFailed(tenantId) }
        assertTrue(savedStatuses.isEmpty())   // the failing listener transaction saves nothing itself
        assertTrue(published.isEmpty())
    }

    @Test
    fun `fails without touching anything when the tenant does not exist`() {
        every { tenantRepository.findById(tenantId) } returns Optional.empty()

        assertThrows<IllegalStateException> { provisioning.on(event) }

        verify(exactly = 0) { migrator.migrateTenantSchema(any()) }
        verify(exactly = 0) { tenantRepository.save(any()) }
        assertTrue(published.isEmpty())
    }
}
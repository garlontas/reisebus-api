package app.reisebus.reisebus_api.platform.provisioning

import app.reisebus.reisebus_api.platform.application.TenantSchemaMigrator
import app.reisebus.reisebus_api.platform.domain.Tenant
import app.reisebus.reisebus_api.platform.domain.TenantStatus
import app.reisebus.reisebus_api.platform.persistence.TenantRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.ApplicationArguments
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals

class TenantMigrationRunnerTest {
    private val tenantRepository = mock<TenantRepository>()
    private val tenantSchemaMigrator = mock<TenantSchemaMigrator>()
    private val clock = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC)
    private val args = mock<ApplicationArguments>()
    private val runner = TenantMigrationRunner(tenantRepository, tenantSchemaMigrator, clock)

    private val statuses = listOf(TenantStatus.ACTIVE, TenantStatus.PROVISIONING)

    private fun givenTenants(vararg tenants: Tenant) {
        whenever(tenantRepository.findByStatusIn(statuses)).thenReturn(tenants.toList())
    }

    private fun failFor(schema: String, ex: Exception = RuntimeException("Migration failed")) {
        doThrow(ex).whenever(tenantSchemaMigrator).migrateTenantSchema(schema)
    }

    @BeforeEach
    fun setUp() {
        whenever(tenantRepository.save(any<Tenant>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `migrates all active and provisioning tenants successfully`() {
        givenTenants(
            createTenant("tenant1", "schema1", TenantStatus.ACTIVE),
            createTenant("tenant2", "schema2", TenantStatus.PROVISIONING)
        )

        runner.run(args)

        verify(tenantSchemaMigrator).migrateTenantSchema("schema1")
        verify(tenantSchemaMigrator).migrateTenantSchema("schema2")
        verify(tenantRepository, never()).save(any())
    }

    @Test
    fun `marks tenant as failed with current clock time when migration throws`() {
        givenTenants(createTenant("tenant1", "schema1", TenantStatus.ACTIVE))
        failFor("schema1")

        runner.run(args)

        val captor = argumentCaptor<Tenant>()
        verify(tenantRepository).save(captor.capture())
        assertEquals(TenantStatus.FAILED, captor.firstValue.status)
        assertEquals(OffsetDateTime.now(clock), captor.firstValue.updatedAt)
    }

    @Test
    fun `continues migrating remaining tenants when one fails`() {
        givenTenants(
            createTenant("tenant1", "schema1", TenantStatus.ACTIVE),
            createTenant("tenant2", "schema2", TenantStatus.ACTIVE),
            createTenant("tenant3", "schema3", TenantStatus.ACTIVE)
        )
        failFor("schema2")

        runner.run(args)

        verify(tenantSchemaMigrator).migrateTenantSchema("schema1")
        verify(tenantSchemaMigrator).migrateTenantSchema("schema2")
        verify(tenantSchemaMigrator).migrateTenantSchema("schema3")
        verify(tenantRepository, times(1)).save(any())
    }

    @Test
    fun `marks multiple failing tenants as failed`() {
        givenTenants(
            createTenant("tenant1", "schema1", TenantStatus.ACTIVE),
            createTenant("tenant2", "schema2", TenantStatus.PROVISIONING),
            createTenant("tenant3", "schema3", TenantStatus.ACTIVE)
        )
        failFor("schema1")
        failFor("schema3")

        runner.run(args)

        verify(tenantRepository, times(2)).save(any())
    }

    @Test
    fun `handles empty tenant list without errors`() {
        givenTenants()

        runner.run(args)

        verify(tenantSchemaMigrator, never()).migrateTenantSchema(any())
        verify(tenantRepository, never()).save(any())
    }

    @Test
    fun `handles all tenants failing to migrate`() {
        givenTenants(
            createTenant("tenant1", "schema1", TenantStatus.ACTIVE),
            createTenant("tenant2", "schema2", TenantStatus.PROVISIONING)
        )
        doThrow(RuntimeException("Migration failed")).whenever(tenantSchemaMigrator).migrateTenantSchema(any())

        runner.run(args)

        verify(tenantRepository, times(2)).save(any())
    }

    @Test
    fun `saves only failed tenants to repository`() {
        val tenant1 = createTenant("tenant1", "schema1", TenantStatus.ACTIVE)
        val tenant2 = createTenant("tenant2", "schema2", TenantStatus.PROVISIONING)
        val tenant3 = createTenant("tenant3", "schema3", TenantStatus.ACTIVE)
        givenTenants(tenant1, tenant2, tenant3)
        failFor("schema2")

        runner.run(args)

        val captor = argumentCaptor<Tenant>()
        verify(tenantRepository, times(1)).save(captor.capture())
        assertEquals(tenant2.id, captor.firstValue.id)
        assertEquals(TenantStatus.FAILED, captor.firstValue.status)
    }

    @Test
    fun `preserves tenant properties when marking as failed`() {
        val id = UUID.randomUUID()
        val tenant = Tenant(
            id = id,
            companyName = "Test Company",
            slug = "test-slug",
            schemaName = "schema_test",
            status = TenantStatus.ACTIVE,
            createdAt = OffsetDateTime.now(clock).minusDays(1),
            updatedAt = OffsetDateTime.now(clock).minusDays(1)
        )
        givenTenants(tenant)
        failFor("schema_test")

        runner.run(args)

        val captor = argumentCaptor<Tenant>()
        verify(tenantRepository).save(captor.capture())
        with(captor.firstValue) {
            assertEquals(id, this.id)
            assertEquals("Test Company", companyName)
            assertEquals("test-slug", slug)
            assertEquals("schema_test", schemaName)
            assertEquals(OffsetDateTime.now(clock).minusDays(1), createdAt)
        }
    }

    @Test
    fun `queries for active and provisioning tenants`() {
        givenTenants()

        runner.run(args)

        verify(tenantRepository).findByStatusIn(statuses)
    }

    @Test
    fun `calls migrator once per tenant in order`() {
        givenTenants(
            createTenant("tenant1", "schema1", TenantStatus.ACTIVE),
            createTenant("tenant2", "schema2", TenantStatus.ACTIVE)
        )

        runner.run(args)

        val order = inOrder(tenantSchemaMigrator)
        order.verify(tenantSchemaMigrator).migrateTenantSchema("schema1")
        order.verify(tenantSchemaMigrator).migrateTenantSchema("schema2")
    }

    @Test
    fun `handles various exception types during migration`() {
        givenTenants(
            createTenant("tenant1", "schema1", TenantStatus.ACTIVE),
            createTenant("tenant2", "schema2", TenantStatus.ACTIVE)
        )
        failFor("schema1", IllegalStateException("Invalid state"))
        failFor("schema2", RuntimeException("Runtime error"))

        runner.run(args)

        verify(tenantRepository, times(2)).save(any())
    }

    private fun createTenant(slug: String, schemaName: String, status: TenantStatus): Tenant {
        val past = OffsetDateTime.now(clock).minusDays(1)
        return Tenant(
            id = UUID.randomUUID(),
            companyName = "Test Company",
            slug = slug,
            schemaName = schemaName,
            status = status,
            createdAt = past,
            updatedAt = past
        )
    }
}
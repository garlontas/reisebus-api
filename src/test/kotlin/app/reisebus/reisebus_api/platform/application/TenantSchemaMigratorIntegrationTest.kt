package app.reisebus.reisebus_api.platform.application

import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Testcontainers
class TenantSchemaMigratorIntegrationTest {
    private val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
    private val jdbc = JdbcTemplate(dataSource)
    private val migrator = TenantSchemaMigrator(dataSource)

    @Test
    fun `creates the tenant schema`() {
        val schema = randomSchemaName()

        migrator.migrateTenantSchema(schema)

        assertTrue(schemaExists(schema))
    }

    @Test
    fun `applies the tenant changelog inside the tenant schema`() {
        val schema = randomSchemaName()

        migrator.migrateTenantSchema(schema)

        assertTrue(tableExists(schema, "booking"))
        assertTrue(indexExists(schema, "idx_booking_status"))
    }

    @Test
    fun `keeps liquibase tracking tables inside the tenant schema`() {
        val schema = randomSchemaName()

        migrator.migrateTenantSchema(schema)

        assertTrue(tableExists(schema, "databasechangelog"))
        assertTrue(tableExists(schema, "databasechangeloglock"))
        assertEquals(1, appliedChangesets(schema))
    }

    @Test
    fun `is idempotent when run twice on the same schema`() {
        val schema = randomSchemaName()

        migrator.migrateTenantSchema(schema)
        migrator.migrateTenantSchema(schema)

        assertTrue(tableExists(schema, "booking"))
        assertEquals(1, appliedChangesets(schema))
    }

    @Test
    fun `migrates a pre-existing empty schema`() {
        val schema = randomSchemaName()
        jdbc.execute("CREATE SCHEMA \"$schema\"")

        migrator.migrateTenantSchema(schema)

        assertTrue(tableExists(schema, "booking"))
        assertEquals(1, appliedChangesets(schema))
    }

    @Test
    fun `isolates tenants from each other and from public`() {
        val schemaA = randomSchemaName()
        val schemaB = randomSchemaName()

        migrator.migrateTenantSchema(schemaA)
        migrator.migrateTenantSchema(schemaB)

        assertTrue(tableExists(schemaA, "booking"))
        assertTrue(tableExists(schemaB, "booking"))
        assertEquals(1, appliedChangesets(schemaA))
        assertEquals(1, appliedChangesets(schemaB))
        assertFalse(tableExists("public", "booking"))
        assertFalse(tableExists("public", "databasechangelog"))
    }

    private fun randomSchemaName() = "t_" + UUID.randomUUID().toString().replace("-", "").take(16)

    private fun schemaExists(schema: String): Boolean =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)",
            Boolean::class.java,
            schema
        )!!

    private fun tableExists(schema: String, table: String): Boolean =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?)",
            Boolean::class.java,
            schema,
            table
        )!!

    private fun indexExists(schema: String, index: String): Boolean =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = ? AND indexname = ?)",
            Boolean::class.java,
            schema,
            index
        )!!

    private fun appliedChangesets(schema: String): Int =
        jdbc.queryForObject("SELECT COUNT(*) FROM \"$schema\".databasechangelog", Int::class.java)!!

    companion object {
        @JvmStatic
        @Container
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
    }
}

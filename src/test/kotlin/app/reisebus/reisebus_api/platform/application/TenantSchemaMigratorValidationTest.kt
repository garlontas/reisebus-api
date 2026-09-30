package app.reisebus.reisebus_api.platform.application

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class TenantSchemaMigratorValidationTest {
    private val dataSource = mock<DataSource>()
    private val migrator = TenantSchemaMigrator(dataSource)

    @ParameterizedTest
    @MethodSource("invalidSchemaNames")
    fun `rejects invalid schema name without touching the database`(schemaName: String) {
        assertFailsWith<IllegalArgumentException> {
            migrator.migrateTenantSchema(schemaName)
        }

        verify(dataSource, never()).connection
    }

    @Test
    fun `rejection carries a descriptive message`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            migrator.migrateTenantSchema("Invalid")
        }

        assertEquals(
            "Schema name must match pattern: start with lowercase letter, contain only lowercase letters, numbers, underscores, 3-41 chars",
            ex.message
        )
    }

    @ParameterizedTest
    @MethodSource("validBoundarySchemaNames")
    fun `accepts schema names at length boundaries`(schemaName: String) {
        // Passing validation means the migrator reaches the database, which fails here on purpose.
        val dbFailure = SQLException("connection refused")
        doThrow(dbFailure).whenever(dataSource).connection

        val ex = assertFailsWith<SQLException> {
            migrator.migrateTenantSchema(schemaName)
        }

        assertSame(dbFailure, ex)
    }

    @Test
    fun `propagates database exceptions to the caller`() {
        val dbFailure = SQLException("connection refused")
        doThrow(dbFailure).whenever(dataSource).connection

        val ex = assertFailsWith<SQLException> {
            migrator.migrateTenantSchema("tenant_valid")
        }

        assertSame(dbFailure, ex)
    }

    companion object {
        @JvmStatic
        fun invalidSchemaNames() = listOf(
            "",
            "   ",
            "ab",
            "a" + "b".repeat(41),
            "1tenant",
            "_tenant",
            "Tenant",
            "tenAnt",
            "ten-ant",
            "ten ant",
            "tenänt",
            "a\"; DROP SCHEMA public; --",
            "tenant\n",
        )

        @JvmStatic
        fun validBoundarySchemaNames() = listOf(
            "abc",
            "a" + "b".repeat(40),
        )
    }
}

package app.reisebus.reisebus_api.platform.application

import liquibase.integration.spring.SpringLiquibase
import org.slf4j.LoggerFactory
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.stereotype.Service
import javax.sql.DataSource
import kotlin.use

@Service
internal class TenantSchemaMigrator(
    private val dataSource: DataSource,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Migrates a tenant schema.
     * Validates the schema name and runs Liquibase migrations.
     * Idempotent: can be safely called multiple times on the same schema.
     *
     * @param schemaName the tenant schema name (must match ^[a-z][a-z0-9_]{2,40}$)
     * @throws IllegalArgumentException if the schema name is invalid
     */
    fun migrateTenantSchema(schemaName: String) {
        require(schemaName.matches(Regex("^[a-z][a-z0-9_]{2,40}$"))) {
            "Schema name must match pattern: start with lowercase letter, contain only lowercase letters, numbers, underscores, 3-41 chars"
        }

        logger.info("Migrating tenant schema: {}", schemaName)
        try {
            this.runMigration(schemaName)
            logger.info("Tenant schema migrated successfully: {}", schemaName)
        } catch (e: Exception) {
            logger.error("Failed to migrate tenant schema: {}", schemaName, e)
            throw e
        }
    }

    private fun runMigration(schemaName: String) {
        dataSource.connection.use { connection ->
            connection.autoCommit = true
            connection.createStatement().use { statement ->
                statement.execute("CREATE SCHEMA IF NOT EXISTS \"$schemaName\"")
            }
        }

        SpringLiquibase().apply {
            dataSource = this@TenantSchemaMigrator.dataSource
            resourceLoader = DefaultResourceLoader()
            changeLog = "classpath:db/changelog/tenant/db.changelog-master.yaml"
            defaultSchema = schemaName
            liquibaseSchema = schemaName
        }.afterPropertiesSet()
    }
}


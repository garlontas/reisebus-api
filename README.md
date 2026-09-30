# Reisebus API

Spring Boot + Kotlin modular monolith with schema-per-tenant multi-tenancy on PostgreSQL.

## Development Setup

### Prerequisites
- Java 25+
- Docker & Docker Compose
- Gradle (or use the included gradlew)

### Quick Start

1. **Start PostgreSQL** (docker-compose)
   ```bash
   docker-compose up -d
   ```

2. **Build and run the application**
   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=dev'
   ```

3. **Access the application**
   - API Documentation: http://localhost:8080/swagger-ui.html
   - API Endpoints: http://localhost:8080/v3/api-docs

### Demo Tenants (Dev Profile)

When running with the `dev` profile, the application automatically provisions two demo tenants:

- **Acme Corp** (`acme`)
  - Domain: `acme.localhost`
  - Schema: `tenant_acme`

- **Beta Industries** (`beta`)
  - Domain: `beta.localhost`
  - Schema: `tenant_beta`

Each tenant has its own isolated PostgreSQL schema (schema-per-tenant isolation).

### Reset Database

To reset the database and start fresh:

```bash
docker-compose down -v
docker-compose up -d
./gradlew bootRun --args='--spring.profiles.active=dev'
```

The `down -v` flag removes all data volumes. On the next startup, the platform schema is created automatically, migrations are applied, and demo tenants are re-provisioned.

## Database

The application uses PostgreSQL with a **schema-per-tenant** isolation model:
- **Platform schema**: Global data (tenants, users, settings)
- **Tenant schemas**: `tenant_<slug>` - isolated per tenant

### Schema Initialization

On startup:
1. The `PlatformSchemaInitializer` creates the `platform` schema if it doesn't exist
2. Liquibase applies all migrations from `db/changelog/platform/`
3. If `app.tenancy.migrate-on-startup=true`, all `ACTIVE` tenants are migrated
4. If `app.dev-seed.enabled=true`, demo tenants are provisioned

### Migrations

- **Platform migrations**: `src/main/resources/db/changelog/platform/`
  - Managed by Spring Boot's auto-configured Liquibase bean
  - Run automatically on startup

- **Tenant migrations**: `src/main/resources/db/changelog/tenant/`
  - Applied manually via `TenantSchemaMigrator.migrateTenantSchema(schemaName)`
  - Each tenant gets its own migration history in its schema

## Testing

Integration tests run against Testcontainers PostgreSQL:

```bash
./gradlew test
```

### Test Setup

- Tests use the `test` profile and Testcontainers (no H2)
- `@IntegrationTest` meta-annotation sets up the shared container
- `@WithTenant(slug)` automatically provisions a tenant schema and sets context
- `TenantFixtures.inTenant(slug) { ... }` for tests working with multiple tenants

### Example Test

```kotlin
@IntegrationTest
class MyTenantTest(
    @Autowired val myService: MyService
) {
    @Test
    @WithTenant("acme")
    fun `should work with acme tenant`() {
        // TenantContext is set to tenant_test_acme
        // All queries use that schema
        myService.doSomething()
    }
}
```

## IDE Setup

**IntelliJ IDEA**
- Open the project root
- Gradle will auto-configure the build
- Run → Edit Configurations → Add "Spring Boot" configuration
  - Main class: `app.reisebus.reisebus_api.ReisebusApiApplicationKt`
  - VM options: `-Dspring.profiles.active=dev`



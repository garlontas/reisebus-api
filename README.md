# Reisebus API

Spring Boot 4 + Kotlin modular monolith (Spring Modulith) with schema-per-tenant multi-tenancy on PostgreSQL.

## Development Setup

### Prerequisites
- JDK 25 (the Gradle toolchain targets Java 25)
- Docker with the Compose plugin (also needed for running tests via Testcontainers)
- No local Gradle install required — use the included `./gradlew`

### Quick Start

1. **Build and run the application with the `dev` profile**
   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=dev'
   ```
   `spring-boot-docker-compose` is on the development classpath, so `bootRun` starts the PostgreSQL service
   from `compose.yaml` automatically and wires the datasource to it. To start the database manually instead:
   ```bash
   docker compose up -d
   ```

2. **Access the application**
   - Swagger UI: http://localhost:8080/swagger-ui.html
   - OpenAPI spec (JSON): http://localhost:8080/v3/api-docs

### Running Tests

```bash
./gradlew test
```

Integration tests start a `postgres:17-alpine` container via Testcontainers, so Docker must be running.

### API

All endpoints are currently open (security permits all requests, CSRF disabled).

| Method | Path                          | Description                                                                 |
|--------|-------------------------------|-----------------------------------------------------------------------------|
| `POST` | `/api/platform/signups`       | Create a tenant. Body: `companyName`, `desiredSlug`, `adminEmail`. Returns `201` with `id` and `status` (`PROVISIONING`); `409` if the slug is taken. |
| `GET`  | `/api/platform/signups/{id}`  | Get the provisioning status of a tenant (`id`, `status`, `shopSlug`, `error`); `404` if unknown. |

### Database

The application uses PostgreSQL with a **schema-per-tenant** isolation model:
- **`platform` schema**: global data — `tenant`, `tenant_domain`, `tenant_settings`, `platform_user`
- **`public` schema**: `event_publication` (Spring Modulith event publication registry)
- **Tenant schemas**: `tenant_<slug>_<12 hex chars of the tenant id>` (hyphens replaced by underscores) — isolated per tenant

Migrations are managed by Liquibase:
- **Platform changelog** (`db/changelog/db.changelog-master.yaml`, includes everything in `changes/`) runs automatically on startup.
- **Tenant changelog** (`db/changelog/tenant/db.changelog-master.yaml`) is applied per tenant schema by `TenantSchemaMigrator`:
  - On signup, `TenantService` publishes a `TenantCreated` event; `TenantProvisioning` handles it asynchronously,
    creates the schema, runs the tenant migrations and marks the tenant `ACTIVE` (or `FAILED` on error).
  - When `app.tenancy.migrate-on-startup=true` (enabled in the `dev` profile, disabled by default), all `ACTIVE`
    and `PROVISIONING` tenant schemas are migrated on startup; tenants that fail are marked `FAILED`.

### Configuration

| File                         | Purpose                                                                        |
|------------------------------|--------------------------------------------------------------------------------|
| `application.yaml`           | Base config; no datasource configured — provide `spring.datasource.*` per environment |
| `application-dev.yaml`       | Local development; enables tenant migration on startup                         |
| `application-test.yaml`      | Tests (`src/test/resources`); datasource comes from Testcontainers             |
| `compose.yaml`               | Local PostgreSQL 17 (`reisebus_dev`, user/password `reisebus`, port `5432`)    |

### IDE Setup

**IntelliJ IDEA**
- Open the project root; Gradle will auto-configure the build
- Run → Edit Configurations → Add "Spring Boot" configuration
  - Main class: `app.reisebus.reisebus_api.ReisebusApiApplicationKt`
  - Active profiles: `dev`

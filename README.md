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
   `spring-boot-docker-compose` is on the development classpath, so `bootRun` starts services from
   `compose.yaml` automatically. To start them manually instead:
   ```bash
   docker compose up -d
   ```

   Local identity provider (Keycloak):
    - Admin Console: http://localhost:8081/admin
    - Realm issuer: `http://localhost:8081/realms/reisebus-dev`

2. **Access the application**
   - Swagger UI: http://localhost:8080/swagger-ui.html
   - OpenAPI spec (JSON): http://localhost:8080/v3/api-docs

### Running Tests

```bash
./gradlew test
```

Integration tests start a `postgres:17-alpine` container and a Keycloak container via Testcontainers,
so Docker must be running.

### API

Authentication is now backed by an external OpenID Connect / OAuth2 provider: public signup endpoints stay open, while
tenant and platform routes require a valid access token (`Authorization: Bearer ...`).

| Method | Path                         | Description                                                                                                                                                   |
|--------|------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `POST` | `/api/platform/signups`      | Create a tenant. Body: `companyName`, `desiredSlug`, `adminEmail`. Returns `201` with `id` and `status` (`PROVISIONING`); `409` if the slug is taken. Public. |
| `GET`  | `/api/platform/signups/{id}` | Get the provisioning status of a tenant (`id`, `status`, `shopSlug`, `error`); `404` if unknown.                                                              |

### JWT / OIDC configuration

JWT validation uses Spring Boot resource server properties:

- `spring.security.oauth2.resourceserver.jwt.issuer-uri`

In this project, `issuer-uri` is mapped from `APP_JWT_ISSUER_URI` (local dev default:
`http://localhost:8081/realms/reisebus-dev`).
With `issuer-uri` set, Spring Security resolves metadata and JWKS automatically via OIDC discovery.

### Database

The application uses PostgreSQL with a **schema-per-tenant** isolation model:
- **`platform` schema**: global data — `tenant`, `tenant_domain`, `tenant_settings`, `platform_user`
- **`public` schema**: `event_publication` (Spring Modulith event publication registry)
- **Tenant schemas**: `tenant_<slug>_<12 hex chars of the tenant id>` (hyphens replaced by underscores) — isolated per tenant

Migrations are managed by Liquibase:
- **Platform changelog** (`db/changelog/db.changelog-master.yaml`, includes everything in `changes/`) runs automatically on startup.
- **Tenant changelog** (`db/changelog/tenant/db.changelog-master.yaml`) is applied per tenant schema by `TenantSchemaMigrator`:
    - On signup, `PlatformService` publishes a `TenantCreated` event; `TenantProvisioning` handles it asynchronously,
    creates the schema, runs the tenant migrations and marks the tenant `ACTIVE` (or `FAILED` on error).
  - When `app.tenancy.migrate-on-startup=true` (enabled in the `dev` profile, disabled by default), all `ACTIVE`
    and `PROVISIONING` tenant schemas are migrated on startup; tenants that fail are marked `FAILED`.

### Configuration

| File                    | Purpose                                                                                         |
|-------------------------|-------------------------------------------------------------------------------------------------|
| `application.yaml`      | Base config; no datasource configured — provide `spring.datasource.*` per environment           |
| `application-dev.yaml`  | Local development; enables tenant migration on startup                                          |
| `application-test.yaml` | Tests (`src/test/resources`); datasource comes from Testcontainers                              |
| `compose.yaml`          | Local PostgreSQL 17 and Keycloak (`http://localhost:8081`, realm import from `infra/keycloak/`) |

### IDE Setup

**IntelliJ IDEA**
- Open the project root; Gradle will auto-configure the build
- Run → Edit Configurations → Add "Spring Boot" configuration
  - Main class: `app.reisebus.reisebus_api.ReisebusApiApplicationKt`
  - Active profiles: `dev`

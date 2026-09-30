package app.reisebus.reisebus_api.platform.service

import java.util.UUID

class DuplicateShopSlugException(val slug: String) :
    RuntimeException("A tenant with slug '$slug' already exists")

class TenantNotFoundException(val tenantId: UUID) :
    RuntimeException("Tenant with id $tenantId not found")

package app.reisebus.reisebus_api.common.tenancy

import app.reisebus.reisebus_api.common.context.TenantContext
import org.hibernate.context.spi.CurrentTenantIdentifierResolver
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component


@Component
class TenantIdentifierResolver(
    @Value($$"${app.tenancy.platform-schema:platform}") private val platformSchema: String,
) : CurrentTenantIdentifierResolver<String> {
    override fun resolveCurrentTenantIdentifier(): String =
        TenantContext.get()?.schemaName ?: platformSchema

    override fun validateExistingCurrentSessions(): Boolean = true
}

package app.reisebus.reisebus_api.common.tenancy

import org.hibernate.cfg.AvailableSettings
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


/** Spring Boot does not pick these two up by bean type; they must be handed to Hibernate explicitly. */
@Configuration
class MultiTenancyConfig {
    @Bean
    fun hibernateMultiTenancy(
        provider: SchemaConnectionProvider,
        resolver: TenantIdentifierResolver,
    ) = HibernatePropertiesCustomizer { props ->
        props[AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER] = provider
        props[AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER] = resolver
    }
}

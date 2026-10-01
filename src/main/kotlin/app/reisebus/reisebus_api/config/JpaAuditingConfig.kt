package app.reisebus.reisebus_api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import java.time.Clock
import java.time.OffsetDateTime
import java.util.*

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
class JpaAuditingConfig {

    @Bean
    fun auditingDateTimeProvider(clock: Clock) = DateTimeProvider {
        Optional.of(OffsetDateTime.now(clock))
    }
}

package app.reisebus.reisebus_api.config

import app.reisebus.reisebus_api.platform.resolver.TenantResolutionFilter
import app.reisebus.reisebus_api.platform.security.AppJwtConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class WebSecurityConfiguration {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        tenantFilter: TenantResolutionFilter,
        jwtAuthenticationConverter: AppJwtConverter
    ): SecurityFilterChain =
        http.authorizeHttpRequests {
            it.requestMatchers(
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/actuator/health",
                "/api/public/**"
            ).permitAll()
                .requestMatchers("/api/signup/**").authenticated()
                .requestMatchers("/api/invitations/**").hasRole("TENANT_ADMIN")
                .requestMatchers("/api/platform/**").hasRole("PLATFORM_ADMIN")
                .anyRequest().authenticated()
        }.csrf { it.disable() }
            .formLogin { form -> form.disable() }
            .httpBasic { basic -> basic.disable() }
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .oauth2ResourceServer { rs -> rs.jwt { it.jwtAuthenticationConverter(jwtAuthenticationConverter) } }
            .addFilterBefore(tenantFilter, BearerTokenAuthenticationFilter::class.java)
            .build()
}

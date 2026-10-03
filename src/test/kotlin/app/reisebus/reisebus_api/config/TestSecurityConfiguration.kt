package app.reisebus.reisebus_api.config


import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain

@TestConfiguration
class TestSecurityConfiguration {

    @Bean
    fun testFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests { it.anyRequest().authenticated() }
        .oauth2ResourceServer { it.jwt(Customizer.withDefaults()) }
        .build()

    // Required by oauth2ResourceServer().jwt(). Real tokens never reach it: tests use jwt() / authentication().
    @Bean
    fun jwtDecoder(): JwtDecoder = JwtDecoder { throw BadJwtException("Not used in slice tests") }
}

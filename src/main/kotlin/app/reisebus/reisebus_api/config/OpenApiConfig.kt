package app.reisebus.reisebus_api.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.security.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun openAPI(
        @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}") issuer: String,
    ): OpenAPI = OpenAPI()
        .components(
            Components().addSecuritySchemes(
                "keycloak",
                SecurityScheme()
                    .type(SecurityScheme.Type.OAUTH2)
                    .flows(
                        OAuthFlows().authorizationCode(
                            OAuthFlow()
                                .authorizationUrl("$issuer/protocol/openid-connect/auth")
                                .tokenUrl("$issuer/protocol/openid-connect/token")
                                .scopes(Scopes().addString("openid", "OpenID Connect"))
                        )
                    )
            )
        )
        .addSecurityItem(SecurityRequirement().addList("keycloak"))
}

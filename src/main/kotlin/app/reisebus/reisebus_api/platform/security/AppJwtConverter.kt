package app.reisebus.reisebus_api.platform.security

import app.reisebus.reisebus_api.common.context.TenantContext
import app.reisebus.reisebus_api.platform.domain.UserStatus
import app.reisebus.reisebus_api.platform.persistence.PlatformUserRepository
import app.reisebus.reisebus_api.platform.persistence.UserRepository
import app.reisebus.reisebus_api.platform.persistence.UserTenantRepository
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.DisabledException
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import java.util.*

@Component
class AppJwtConverter(
    private val users: UserRepository,
    private val memberships: UserTenantRepository,
    private val platformUsers: PlatformUserRepository,
) : Converter<Jwt, AbstractAuthenticationToken> {

    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val user = users.findByIdentityIssuerAndIdentitySubject(jwt.issuer.toString(), jwt.subject)
        val authorities = mutableListOf<GrantedAuthority>()

        if (user == null) {
            return AppAuthentication(jwt, authorities, null)
        }

        if (user.status == UserStatus.DISABLED) {
            throw DisabledException("User is disabled")
        }

        TenantContext.get()?.let { tenant ->
            memberships.findByIdUserIdAndIdTenantId(user.id, tenant.id)?.let { membership ->
                authorities += SimpleGrantedAuthority("ROLE_${membership.role.name}")
            }
        }

        if (platformUsers.existsById(user.id)) {
            authorities += SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")
        }

        return AppAuthentication(jwt, authorities, user.id)
    }
}

class AppAuthentication(jwt: Jwt, authorities: Collection<GrantedAuthority>, val userId: UUID?) :
    JwtAuthenticationToken(jwt, authorities)

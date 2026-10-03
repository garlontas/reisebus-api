package app.reisebus.reisebus_api.platform.service

import app.reisebus.reisebus_api.common.tenancy.verifiedEmail
import app.reisebus.reisebus_api.platform.domain.User
import app.reisebus.reisebus_api.platform.domain.UserStatus
import app.reisebus.reisebus_api.platform.persistence.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.*


@Service
class UserProvisioningService(private val users: UserRepository) {

    fun findByJwt(jwt: Jwt): User? =
        users.findByIdentityIssuerAndIdentitySubject(jwt.issuer.toString(), jwt.subject)

    /** JIT provisioning: the IdP knows the person first, our DB creates the row on first signup/invitation. */
    @Transactional
    fun getOrCreate(jwt: Jwt): User {
        findByJwt(jwt)?.let { return it }

        val email = jwt.verifiedEmail()
        if (users.existsByEmail(email)) {
            // Same email, different (issuer, sub). Never auto-link: that would allow account takeover.
            throw ResponseStatusException(HttpStatus.CONFLICT, "This email is already registered with another identity")
        }
        return users.save(
            User(
                id = UUID.randomUUID(),
                email = email,
                identityIssuer = jwt.issuer.toString(),
                identitySubject = jwt.subject
                    ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT subject is missing"),
                firstName = jwt.getClaimAsString("given_name").orEmpty(),
                lastName = jwt.getClaimAsString("family_name").orEmpty(),
                status = UserStatus.ACTIVE,
            )
        )
    }
}

package app.reisebus.reisebus_api.common.tenancy

import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.server.ResponseStatusException

internal fun Jwt.verifiedEmail(): String {
    if (getClaimAsBoolean("email_verified") != true) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN, "Email address is not verified")
    }
    return getClaimAsString("email")?.trim()?.lowercase()
        ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Token contains no email claim")
}

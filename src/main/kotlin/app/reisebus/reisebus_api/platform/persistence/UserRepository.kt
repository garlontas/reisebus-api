package app.reisebus.reisebus_api.platform.persistence

import app.reisebus.reisebus_api.platform.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
    fun findByIdentityIssuerAndIdentitySubject(issuer: String, subject: String?): User?
}



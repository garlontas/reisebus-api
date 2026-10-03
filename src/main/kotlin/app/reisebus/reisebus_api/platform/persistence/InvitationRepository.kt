package app.reisebus.reisebus_api.platform.persistence

import app.reisebus.reisebus_api.platform.domain.Invitation
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import java.util.*

interface InvitationRepository : JpaRepository<Invitation, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByTokenHash(tokenHash: String): Invitation?
}

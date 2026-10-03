package app.reisebus.reisebus_api.platform.persistence

import app.reisebus.reisebus_api.platform.domain.PlatformUser
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface PlatformUserRepository : JpaRepository<PlatformUser, UUID>

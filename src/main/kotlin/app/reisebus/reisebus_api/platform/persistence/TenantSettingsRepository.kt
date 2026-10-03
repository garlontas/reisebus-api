package app.reisebus.reisebus_api.platform.persistence

import app.reisebus.reisebus_api.platform.domain.TenantSettings
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface TenantSettingsRepository : JpaRepository<TenantSettings, UUID>

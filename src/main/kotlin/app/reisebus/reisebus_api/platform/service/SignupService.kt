package app.reisebus.reisebus_api.platform.service

import app.reisebus.reisebus_api.platform.api.contract.SignupProvisioningStatus
import app.reisebus.reisebus_api.platform.domain.TenantSettings
import app.reisebus.reisebus_api.platform.domain.UserTenant
import app.reisebus.reisebus_api.platform.domain.UserTenantId
import app.reisebus.reisebus_api.platform.domain.UserTenantRole
import app.reisebus.reisebus_api.platform.messaging.CreateTenant
import app.reisebus.reisebus_api.platform.messaging.TenantCreated
import app.reisebus.reisebus_api.platform.persistence.TenantSettingsRepository
import app.reisebus.reisebus_api.platform.persistence.UserTenantRepository
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class SignupService(
    private val platformService: PlatformService,
    private val userProvisioning: UserProvisioningService,
    private val memberships: UserTenantRepository,
    private val settings: TenantSettingsRepository,
) {
    /**
     * One transaction: user + tenant (PROVISIONING) + settings + admin membership.
     * TenantCreated is published inside it; the schema provisioning listener must run AFTER_COMMIT
     * (@ApplicationModuleListener), otherwise it could run before the membership exists.
     */
    @Transactional
    fun registerTenant(jwt: Jwt, cmd: CreateTenant): TenantCreated {
        val user = userProvisioning.getOrCreate(jwt)
        val tenant = platformService.createTenant(cmd)

        memberships.save(UserTenant(UserTenantId(user.id, tenant.tenantId), UserTenantRole.TENANT_ADMIN))
        settings.save(TenantSettings(tenantId = tenant.tenantId, contactEmail = user.email, branding = mapOf()))
        return tenant
    }

    /** Only members may poll the provisioning status; everyone else gets a 404. */
    @Transactional(readOnly = true)
    fun provisioningStatus(userId: UUID, tenantId: UUID): SignupProvisioningStatus {
        if (!memberships.existsByIdUserIdAndIdTenantId(userId, tenantId)) throw TenantNotFoundException(tenantId)
        return platformService.getSignupProvisioningStatus(tenantId)
    }
}

package app.reisebus.reisebus_api.platform.api

import app.reisebus.reisebus_api.common.context.TenantContext
import app.reisebus.reisebus_api.common.context.TenantInfo
import app.reisebus.reisebus_api.config.TestSecurityConfiguration
import app.reisebus.reisebus_api.platform.resolver.TenantResolutionFilter
import app.reisebus.reisebus_api.platform.security.AppAuthentication
import app.reisebus.reisebus_api.platform.security.AppJwtConverter
import app.reisebus.reisebus_api.platform.service.MeResponse
import app.reisebus.reisebus_api.platform.service.MeService
import app.reisebus.reisebus_api.platform.service.TenantMembership
import app.reisebus.reisebus_api.platform.service.UserSummary
import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE
import org.springframework.context.annotation.Import
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.*

/**
 * The controller takes `auth: AppAuthentication`, so requests must carry exactly that type
 * (authentication(appAuth(...))). A plain jwt() post-processor produces a JwtAuthenticationToken
 * and the argument cannot be resolved.
 */
@WebMvcTest(
    controllers = [MeController::class],
    excludeFilters = [ComponentScan.Filter(
        type = ASSIGNABLE_TYPE,
        classes = [TenantResolutionFilter::class, AppJwtConverter::class],
    )],
)
@Import(TestSecurityConfiguration::class)
class MeControllerTest(
    @Autowired private val mvc: MockMvc,
) {
    @MockkBean
    private lateinit var meService: MeService

    private val userId = UUID.randomUUID()
    private val tenant = TenantInfo(UUID.randomUUID(), "tenant_acme_0123456789ab")

    @AfterEach
    fun tearDown() {
        TenantContext.clear()   // static ThreadLocal: must not leak into the next test
        clearMocks(meService)
    }

    @Test
    fun `GET me returns user, current tenant and all tenants`() {
        val acme = membership("acme", "Acme Tours", "ACTIVE", "tenant_admin", tenant.id)
        val globex = membership("globex", "Globex Reisen", "ACTIVE", "driver")
        TenantContext.set(tenant)
        every { meService.load(userId, tenant, false) } returns
                meResponse(current = acme, tenants = listOf(acme, globex))

        mvc.get(ME_URL) { with(authentication(appAuth(userId))) }.andExpect {
            status { isOk() }
            jsonPath("$.user.id") { value(userId.toString()) }
            jsonPath("$.user.email") { value("admin@example.com") }
            jsonPath("$.user.firstName") { value("Ada") }
            jsonPath("$.platformAdmin") { value(false) }
            jsonPath("$.currentTenant.slug") { value("acme") }
            jsonPath("$.currentTenant.role") { value("tenant_admin") }
            jsonPath("$.tenants.length()") { value(2) }
            jsonPath("$.tenants[0].host") { value("acme.localhost") }   // frontend redirects here
            jsonPath("$.tenants[1].slug") { value("globex") }
        }
    }

    @Test
    fun `GET me passes user id from the authentication and tenant from the context to the service`() {
        TenantContext.set(tenant)
        every { meService.load(any(), any(), any()) } returns meResponse()

        mvc.get(ME_URL) { with(authentication(appAuth(userId))) }.andExpect { status { isOk() } }

        verify(exactly = 1) { meService.load(userId, tenant, false) }
    }

    @Test
    fun `GET me without tenant context has no current tenant`() {
        val acme = membership("acme", "Acme Tours", "ACTIVE", "tenant_admin")
        every { meService.load(userId, null, false) } returns meResponse(tenants = listOf(acme))   // root domain

        mvc.get(ME_URL) { with(authentication(appAuth(userId))) }.andExpect {
            status { isOk() }
            jsonPath("$.currentTenant") { isEmpty() }
            jsonPath("$.tenants.length()") { value(1) }
        }

        verify(exactly = 1) { meService.load(userId, null, false) }
    }

    @Test
    fun `GET me reports platform admin when the authentication has the platform role`() {
        every { meService.load(userId, null, true) } returns meResponse(platformAdmin = true)

        mvc.get(ME_URL) {
            with(authentication(appAuth(userId, "ROLE_PLATFORM_ADMIN")))
        }.andExpect {
            status { isOk() }
            jsonPath("$.platformAdmin") { value(true) }
        }

        verify(exactly = 1) { meService.load(userId, null, true) }
    }

    @Test
    fun `GET me does not treat tenant roles as platform admin`() {
        every { meService.load(userId, null, false) } returns meResponse()

        mvc.get(ME_URL) {
            with(authentication(appAuth(userId, "ROLE_TENANT_ADMIN", "ROLE_STAFF")))
        }.andExpect { status { isOk() } }

        verify(exactly = 1) { meService.load(userId, null, false) }
    }

    @Test
    fun `GET me exposes the provisioning status of a tenant`() {
        val provisioning = membership("new-shop", "New Shop", "PROVISIONING", "tenant_admin")
        every { meService.load(userId, null, false) } returns meResponse(tenants = listOf(provisioning))

        mvc.get(ME_URL) { with(authentication(appAuth(userId))) }.andExpect {
            status { isOk() }
            jsonPath("$.tenants[0].status") { value("PROVISIONING") }
        }
    }

    @Test
    fun `GET me for a user without tenants returns an empty list`() {
        every { meService.load(userId, null, false) } returns meResponse()

        mvc.get(ME_URL) { with(authentication(appAuth(userId))) }.andExpect {
            status { isOk() }
            jsonPath("$.tenants") { isEmpty() }
        }
    }

    @Test
    fun `GET me without a local user returns 404 and does not call the service`() {
        // valid IdP login, but the signup has not happened yet -> frontend shows the signup screen
        mvc.get(ME_URL) { with(authentication(appAuth(userId = null))) }.andExpect {
            status { isNotFound() }
        }

        verify(exactly = 0) { meService.load(any(), any(), any()) }
    }

    // Needs a TestSecurityConfiguration with anyRequest().authenticated(); remove if yours permits all.
    @Test
    fun `GET me without token returns 401 and does not call the service`() {
        mvc.get(ME_URL).andExpect { status { isUnauthorized() } }

        verify(exactly = 0) { meService.load(any(), any(), any()) }
    }

    // ------------------------------------------------------------------ helpers

    private fun appAuth(userId: UUID?, vararg authorities: String) = AppAuthentication(
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("kc-sub")
            .claim("email", "admin@example.com")
            .build(),
        authorities.map<String, GrantedAuthority> { SimpleGrantedAuthority(it) },
        userId,
    )

    private fun meResponse(
        platformAdmin: Boolean = false,
        current: TenantMembership? = null,
        tenants: List<TenantMembership> = emptyList(),
    ) = MeResponse(
        user = UserSummary(userId, "admin@example.com", "Ada", "Admin"),
        platformAdmin = platformAdmin,
        currentTenant = current,
        tenants = tenants,
    )

    private fun membership(
        slug: String,
        companyName: String,
        status: String,
        role: String,
        tenantId: UUID = UUID.randomUUID(),
    ) = TenantMembership(
        tenantId = tenantId,
        companyName = companyName,
        slug = slug,
        host = "$slug.localhost",
        status = status,
        role = role,
    )

    private companion object {
        const val ME_URL = "/api/me"
    }
}
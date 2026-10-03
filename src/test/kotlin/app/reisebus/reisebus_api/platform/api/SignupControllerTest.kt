package app.reisebus.reisebus_api.platform.api

import app.reisebus.reisebus_api.config.TestSecurityConfiguration
import app.reisebus.reisebus_api.platform.api.contract.SignupProvisioningStatus
import app.reisebus.reisebus_api.platform.api.contract.TenantCreationStatus
import app.reisebus.reisebus_api.platform.domain.TenantStatus
import app.reisebus.reisebus_api.platform.messaging.CreateTenant
import app.reisebus.reisebus_api.platform.messaging.TenantCreated
import app.reisebus.reisebus_api.platform.resolver.TenantResolutionFilter
import app.reisebus.reisebus_api.platform.security.AppAuthentication
import app.reisebus.reisebus_api.platform.security.AppJwtConverter
import app.reisebus.reisebus_api.platform.service.*
import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.server.ResponseStatusException
import java.util.*
import kotlin.test.assertEquals

@WebMvcTest(
    controllers = [SignupController::class],
    excludeFilters = [ComponentScan.Filter(
        type = ASSIGNABLE_TYPE,
        classes = [TenantResolutionFilter::class, AppJwtConverter::class],
    )],
)
@Import(TestSecurityConfiguration::class)
class SignupControllerTest(
    @Autowired private val mvc: MockMvc,
) {
    @MockkBean
    private lateinit var signupService: SignupService

    @MockkBean
    private lateinit var platformService: PlatformService

    @MockkBean
    private lateinit var invitationService: InvitationService

    @AfterEach
    fun tearDown() = clearMocks(signupService, platformService, invitationService)

    // ------------------------------------------------------------------ POST /api/signup/tenant

    @Test
    fun `POST tenant returns 201 with location and forwards token and request to the service`() {
        val tenantId = UUID.randomUUID()
        val jwtSlot = slot<Jwt>()
        val requestSlot = slot<CreateTenant>()
        every { signupService.registerTenant(capture(jwtSlot), capture(requestSlot)) } returns
                tenantCreated(tenantId, TenantStatus.PROVISIONING)

        mvc.post(TENANT_URL) {
            with(jwt().jwt { it.subject("kc-sub-1").claim("email", "admin@example.com") })
            contentType = MediaType.APPLICATION_JSON
            content = createTenantJson()
        }.andExpect {
            status { isCreated() }
            header { string("Location", "/api/signup/tenants/$tenantId") }
            jsonPath("$.id") { value(tenantId.toString()) }
            jsonPath("$.status") { value("PROVISIONING") }
        }

        // identity comes from the token, company data from the body
        assertEquals("kc-sub-1", jwtSlot.captured.subject)
        assertEquals(" Reisebus GmbH ", requestSlot.captured.companyName)
        assertEquals("Example-Bus", requestSlot.captured.shopSlug)
    }

    @ParameterizedTest
    @EnumSource(TenantStatus::class)
    fun `POST tenant returns the status reported by the service`(status: TenantStatus) {
        every { signupService.registerTenant(any(), any()) } returns tenantCreated(UUID.randomUUID(), status)

        mvc.post(TENANT_URL) {
            with(jwt())
            contentType = MediaType.APPLICATION_JSON
            content = createTenantJson()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.status") { value(status.name) }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `POST tenant with blank company name returns 400 and does not call the service`(companyName: String) {
        mvc.post(TENANT_URL) {
            with(jwt())
            contentType = MediaType.APPLICATION_JSON
            content = createTenantJson(companyName = companyName)
        }.andExpect { status { isBadRequest() } }

        verify(exactly = 0) { signupService.registerTenant(any(), any()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `POST tenant with blank shop slug returns 400 and does not call the service`(desiredSlug: String) {
        mvc.post(TENANT_URL) {
            with(jwt())
            contentType = MediaType.APPLICATION_JSON
            content = createTenantJson(desiredSlug = desiredSlug)
        }.andExpect { status { isBadRequest() } }

        verify(exactly = 0) { signupService.registerTenant(any(), any()) }
    }

    @Test
    fun `POST tenant with malformed JSON returns 400`() {
        mvc.post(TENANT_URL) {
            with(jwt())
            contentType = MediaType.APPLICATION_JSON
            content = "{ not json"
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `POST tenant with duplicate slug returns 409`() {
        every { signupService.registerTenant(any(), any()) } throws DuplicateShopSlugException("example-bus")

        mvc.post(TENANT_URL) {
            with(jwt())
            contentType = MediaType.APPLICATION_JSON
            content = createTenantJson(desiredSlug = "example-bus")
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `POST tenant with unverified email returns 403`() {
        every { signupService.registerTenant(any(), any()) } throws
                ResponseStatusException(HttpStatus.FORBIDDEN, "Email address is not verified")

        mvc.post(TENANT_URL) {
            with(jwt())
            contentType = MediaType.APPLICATION_JSON
            content = createTenantJson()
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `POST tenant with email registered under another identity returns 409`() {
        every { signupService.registerTenant(any(), any()) } throws
                ResponseStatusException(HttpStatus.CONFLICT, "This email is already registered with another identity")

        mvc.post(TENANT_URL) {
            with(jwt())
            contentType = MediaType.APPLICATION_JSON
            content = createTenantJson()
        }.andExpect { status { isConflict() } }
    }

    // ------------------------------------------------------------------ GET /api/signup/tenants/{id}

    @ParameterizedTest
    @EnumSource(TenantCreationStatus::class)
    fun `GET provisioning status returns the status reported by the service`(status: TenantCreationStatus) {
        val tenantId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        every { signupService.provisioningStatus(userId, tenantId) } returns
                SignupProvisioningStatus(id = tenantId, status = status, shopSlug = "test-slug", error = null)

        mvc.get(STATUS_URL, tenantId) {
            with(authentication(appAuth(userId)))
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(tenantId.toString()) }
            jsonPath("$.status") { value(status.name) }
            jsonPath("$.shopSlug") { value("test-slug") }
        }

        // the user id comes from the authentication, not from the request
        verify(exactly = 1) { signupService.provisioningStatus(userId, tenantId) }
    }

    @Test
    fun `GET provisioning status exposes the error when provisioning failed`() {
        val tenantId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        every { signupService.provisioningStatus(userId, tenantId) } returns
                SignupProvisioningStatus(
                    id = tenantId,
                    status = TenantCreationStatus.FAILED,
                    shopSlug = "failed-slug",
                    error = "Provisioning failed",
                )

        mvc.get(STATUS_URL, tenantId) {
            with(authentication(appAuth(userId)))
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("FAILED") }
            jsonPath("$.error") { value("Provisioning failed") }
        }
    }

    @Test
    fun `GET provisioning status with malformed id returns 400`() {
        mvc.get(STATUS_URL, "not-a-uuid") {
            with(authentication(appAuth(UUID.randomUUID())))
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `GET provisioning status for unknown tenant or foreign tenant returns 404`() {
        val tenantId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        every { signupService.provisioningStatus(userId, tenantId) } throws TenantNotFoundException(tenantId)

        mvc.get(STATUS_URL, tenantId) {
            with(authentication(appAuth(userId)))
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `GET provisioning status without a local user returns 404 and does not call the service`() {
        mvc.get(STATUS_URL, UUID.randomUUID()) {
            with(authentication(appAuth(userId = null)))
        }.andExpect { status { isNotFound() } }

        verify(exactly = 0) { signupService.provisioningStatus(any(), any()) }
    }

    // ------------------------------------------------------------------ GET /api/signup/slug-availability

    @Test
    fun `GET slug availability for unused slug returns true`() {
        every { platformService.isDesiredSlugAvailable("example-bus") } returns true

        mvc.get(SLUG_URL, "example-bus") { with(jwt()) }.andExpect {
            status { isOk() }
            jsonPath("$.slug") { value("example-bus") }
            jsonPath("$.available") { value(true) }
        }
    }

    @Test
    fun `GET slug availability for used slug returns false`() {
        every { platformService.isDesiredSlugAvailable("example-bus") } returns false

        mvc.get(SLUG_URL, "example-bus") { with(jwt()) }.andExpect {
            status { isOk() }
            jsonPath("$.slug") { value("example-bus") }
            jsonPath("$.available") { value(false) }
        }
    }

    @Test
    fun `GET slug availability returns the normalized slug`() {
        every { platformService.isDesiredSlugAvailable("Example-Bus") } returns true

        mvc.get(SLUG_URL, "Example-Bus") { with(jwt()) }.andExpect {
            status { isOk() }
            jsonPath("$.slug") { value("example-bus") }
            jsonPath("$.available") { value(true) }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "invalid_slug", "-bad-"])
    fun `GET slug availability for invalid slug returns 400`(slug: String) {
        every { platformService.isDesiredSlugAvailable(slug) } throws
                IllegalArgumentException("Shop slug must contain only lowercase letters, numbers, and hyphens")

        mvc.get(SLUG_URL, slug) { with(jwt()) }.andExpect { status { isBadRequest() } }
    }

    // ------------------------------------------------------------------ POST /api/signup/accept-invitation

    @Test
    fun `POST accept-invitation returns the tenant and forwards token and jwt to the service`() {
        val jwtSlot = slot<Jwt>()
        every { invitationService.accept(capture(jwtSlot), "raw-token") } returns
                AcceptedInvitation(tenantSlug = "acme", host = "acme.localhost")

        mvc.post(ACCEPT_URL) {
            with(jwt().jwt { it.subject("kc-sub-2").claim("email", "driver@example.com") })
            contentType = MediaType.APPLICATION_JSON
            content = acceptJson("raw-token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.tenantSlug") { value("acme") }
            jsonPath("$.host") { value("acme.localhost") }
        }

        assertEquals("kc-sub-2", jwtSlot.captured.subject)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `POST accept-invitation with blank token returns 400 and does not call the service`(token: String) {
        mvc.post(ACCEPT_URL) {
            with(jwt())
            contentType = MediaType.APPLICATION_JSON
            content = acceptJson(token)
        }.andExpect { status { isBadRequest() } }

        verify(exactly = 0) { invitationService.accept(any(), any()) }
    }

    @Test
    fun `POST accept-invitation with malformed JSON returns 400`() {
        mvc.post(ACCEPT_URL) {
            with(jwt())
            contentType = MediaType.APPLICATION_JSON
            content = "{ not json"
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `POST accept-invitation with invalid or expired token returns 410`() {
        every { invitationService.accept(any(), any()) } throws
                ResponseStatusException(HttpStatus.GONE, "Invitation is invalid or expired")

        mvc.post(ACCEPT_URL) {
            with(jwt())
            contentType = MediaType.APPLICATION_JSON
            content = acceptJson("expired-token")
        }.andExpect { status { isGone() } }
    }

    @Test
    fun `POST accept-invitation for a different email returns 403`() {
        every { invitationService.accept(any(), any()) } throws
                ResponseStatusException(HttpStatus.FORBIDDEN, "Invitation was issued for a different email address")

        mvc.post(ACCEPT_URL) {
            with(jwt())
            contentType = MediaType.APPLICATION_JSON
            content = acceptJson("raw-token")
        }.andExpect { status { isForbidden() } }
    }

    // ------------------------------------------------------------------ helpers

    private fun tenantCreated(id: UUID, status: TenantStatus) = TenantCreated(
        tenantId = id,
        companyName = "Reisebus GmbH",
        shopSlug = "example-bus",
        schemaName = "tenant_example_bus_${id.toString().replace("-", "").take(12)}",
        status = status,
    )

    /** Same type the production converter produces, so `auth: AppAuthentication` resolves in the controller. */
    private fun appAuth(userId: UUID?) = AppAuthentication(
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("kc-sub")
            .claim("email", "admin@example.com")
            .build(),
        listOf(SimpleGrantedAuthority("ROLE_TENANT_ADMIN")),
        userId,
    )

    private fun createTenantJson(
        companyName: String = " Reisebus GmbH ",
        desiredSlug: String = "Example-Bus",
    ) = """{"companyName":"$companyName","desiredSlug":"$desiredSlug"}"""

    private fun acceptJson(token: String) = """{"token":"$token"}"""

    private companion object {
        const val TENANT_URL = "/api/signup/tenant"
        const val STATUS_URL = "/api/signup/tenants/{id}"
        const val SLUG_URL = "/api/signup/slug-availability?slug={slug}"
        const val ACCEPT_URL = "/api/signup/accept-invitation"
    }
}

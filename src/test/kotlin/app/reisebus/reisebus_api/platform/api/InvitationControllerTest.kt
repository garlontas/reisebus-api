package app.reisebus.reisebus_api.platform.api

import app.reisebus.reisebus_api.common.context.TenantContext
import app.reisebus.reisebus_api.common.context.TenantInfo
import app.reisebus.reisebus_api.config.TestSecurityConfiguration
import app.reisebus.reisebus_api.platform.resolver.TenantResolutionFilter
import app.reisebus.reisebus_api.platform.security.AppJwtConverter
import app.reisebus.reisebus_api.platform.service.InvitationService
import app.reisebus.reisebus_api.platform.service.TenantNotFoundException
import com.ninjasquad.springmockk.MockkBean
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.web.server.ResponseStatusException
import java.util.*

/**
 * Controller behaviour only. Who may call this endpoint (TENANT_ADMIN) is decided by SecurityConfig
 * and is not covered here (see the authorization test with the real SecurityConfig).
 *
 * TenantResolutionFilter is excluded, so each test sets TenantContext itself; MockMvc runs the
 * request on the test thread, so the ThreadLocal is visible to the controller.
 */
@WebMvcTest(
    controllers = [InvitationController::class],
    excludeFilters = [ComponentScan.Filter(
        type = ASSIGNABLE_TYPE,
        classes = [TenantResolutionFilter::class, AppJwtConverter::class],
    )],
)
@Import(TestSecurityConfiguration::class)
class InvitationControllerTest(
    @Autowired private val mvc: MockMvc,
) {
    @MockkBean
    private lateinit var invitationService: InvitationService

    private val tenant = TenantInfo(UUID.randomUUID(), "tenant_acme_0123456789ab")

    @BeforeEach
    fun setUp() {
        TenantContext.set(tenant)
        every { invitationService.invite(any(), any(), any()) } just Runs
    }

    @AfterEach
    fun tearDown() {
        TenantContext.clear()
        clearMocks(invitationService)
    }

    @ParameterizedTest
    @ValueSource(strings = ["STAFF", "DRIVER"])
    fun `POST invitation returns 201 without body and forwards tenant, email and role`(role: String) {
        mvc.post(INVITATION_URL) {
            with(jwt())
            contentType = MediaType.APPLICATION_JSON
            content = inviteJson(role = role)
        }.andExpect {
            status { isCreated() }
            content { string("") }
        }

        // the tenant comes from the request context (host), never from the body
        verify(exactly = 1) {
            invitationService.invite(tenant.id, "driver@example.com", match { it.name == role })
        }
    }

    @Test
    fun `POST invitation without tenant context returns 400 and does not call the service`() {
        TenantContext.clear()

        mvc.post(INVITATION_URL) {
            with(jwt())
            contentType = MediaType.APPLICATION_JSON
            content = inviteJson()
        }.andExpect { status { isBadRequest() } }

        verify(exactly = 0) { invitationService.invite(any(), any(), any()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "not-an-email", "missing-at.example.com"])
    fun `POST invitation with invalid email returns 400 and does not call the service`(email: String) {
        mvc.post(INVITATION_URL) {
            with(jwt())
            contentType = MediaType.APPLICATION_JSON
            content = inviteJson(email = email)
        }.andExpect { status { isBadRequest() } }

        verify(exactly = 0) { invitationService.invite(any(), any(), any()) }
    }

    @Test
    fun `POST invitation with unknown role returns 400 and does not call the service`() {
        mvc.post(INVITATION_URL) {
            with(jwt())
            contentType = MediaType.APPLICATION_JSON
            content = inviteJson(role = "NOT_A_ROLE")
        }.andExpect { status { isBadRequest() } }

        verify(exactly = 0) { invitationService.invite(any(), any(), any()) }
    }

    @Test
    fun `POST invitation without role returns 400 and does not call the service`() {
        mvc.post(INVITATION_URL) {
            with(jwt())
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"driver@example.com"}"""
        }.andExpect { status { isBadRequest() } }

        verify(exactly = 0) { invitationService.invite(any(), any(), any()) }
    }

    @Test
    fun `POST invitation with malformed JSON returns 400`() {
        mvc.post(INVITATION_URL) {
            with(jwt())
            contentType = MediaType.APPLICATION_JSON
            content = "{ not json"
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `POST invitation for existing member returns 409`() {
        every { invitationService.invite(any(), any(), any()) } throws
                ResponseStatusException(HttpStatus.CONFLICT, "User is already a member of this tenant")

        mvc.post(INVITATION_URL) {
            with(jwt())
            contentType = MediaType.APPLICATION_JSON
            content = inviteJson()
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `POST invitation for unknown tenant returns 404`() {
        every { invitationService.invite(any(), any(), any()) } throws TenantNotFoundException(tenant.id)

        mvc.post(INVITATION_URL) {
            with(jwt())
            contentType = MediaType.APPLICATION_JSON
            content = inviteJson()
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `POST invitation without token returns 401 and does not call the service`() {
        mvc.post(INVITATION_URL) {
            contentType = MediaType.APPLICATION_JSON
            content = inviteJson()
        }.andExpect { status { isUnauthorized() } }

        verify(exactly = 0) { invitationService.invite(any(), any(), any()) }
    }

    private fun inviteJson(
        email: String = "driver@example.com",
        role: String = "DRIVER",
    ) = """{"email":"$email","role":"$role"}"""

    private companion object {
        const val INVITATION_URL = "/api/invitations"
    }
}

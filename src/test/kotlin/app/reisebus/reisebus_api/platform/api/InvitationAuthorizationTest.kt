package app.reisebus.reisebus_api.platform.api

import app.reisebus.reisebus_api.common.context.TenantInfo
import app.reisebus.reisebus_api.config.WebSecurityConfiguration
import app.reisebus.reisebus_api.platform.resolver.TenantResolutionFilter
import app.reisebus.reisebus_api.platform.resolver.TenantResolver
import app.reisebus.reisebus_api.platform.security.AppJwtConverter
import app.reisebus.reisebus_api.platform.service.InvitationService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.*

@WebMvcTest(InvitationController::class)
@Import(WebSecurityConfiguration::class, TenantResolutionFilter::class)
class InvitationAuthorizationTest(@Autowired private val mvc: MockMvc) {

    @MockkBean
    private lateinit var invitationService: InvitationService
    @MockkBean
    private lateinit var tenantResolver: TenantResolver
    @MockkBean
    private lateinit var jwtConverter: AppJwtConverter
    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    @BeforeEach
    fun setUp() {
        every { tenantResolver.resolveByHost("acme.localhost") } returns
                TenantInfo(UUID.randomUUID(), "tenant_acme_0123456789ab")
        every { invitationService.invite(any(), any(), any()) } just Runs
    }

    private val body = """{"email":"driver@example.com","role":"DRIVER"}"""
    private val url = "http://acme.localhost/api/invitations"

    @Test
    fun `without token returns 401`() {
        mvc.post(url) { contentType = MediaType.APPLICATION_JSON; content = body }
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `staff is forbidden`() {
        mvc.post(url) {
            with(jwt().authorities(SimpleGrantedAuthority("ROLE_STAFF")))
            contentType = MediaType.APPLICATION_JSON; content = body
        }.andExpect { status { isForbidden() } }
        verify(exactly = 0) { invitationService.invite(any(), any(), any()) }
    }

    @Test
    fun `tenant admin is allowed`() {
        mvc.post(url) {
            with(jwt().authorities(SimpleGrantedAuthority("ROLE_TENANT_ADMIN")))
            contentType = MediaType.APPLICATION_JSON; content = body
        }.andExpect { status { isCreated() } }
    }
}

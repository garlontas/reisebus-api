package app.reisebus.reisebus_api.platform.security

import app.reisebus.reisebus_api.common.context.TenantContext
import app.reisebus.reisebus_api.common.context.TenantInfo
import app.reisebus.reisebus_api.platform.domain.*
import app.reisebus.reisebus_api.platform.persistence.PlatformUserRepository
import app.reisebus.reisebus_api.platform.persistence.UserRepository
import app.reisebus.reisebus_api.platform.persistence.UserTenantRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.security.authentication.DisabledException
import org.springframework.security.oauth2.jwt.Jwt
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plain unit test, no Spring context. This converter is the one place that decides
 * "who is this user in THIS tenant", so every branch is covered here.
 */
class AppJwtConverterTest {

    private val users = mockk<UserRepository>()
    private val memberships = mockk<UserTenantRepository>()
    private val platformUsers = mockk<PlatformUserRepository>()
    private val converter = AppJwtConverter(users, memberships, platformUsers)

    private val user = user()
    private val tenantA = TenantInfo(UUID.randomUUID(), "tenant_acme_0123456789ab")
    private val tenantB = TenantInfo(UUID.randomUUID(), "tenant_globex_ba9876543210")

    @AfterEach
    fun clearTenantContext() = TenantContext.clear()

    // ------------------------------------------------------------------ membership -> role

    @ParameterizedTest
    @CsvSource(
        "TENANT_ADMIN,ROLE_TENANT_ADMIN",
        "STAFF,ROLE_STAFF",
        "DRIVER,ROLE_DRIVER",
    )
    fun `member of the requested tenant gets the matching role`(role: UserTenantRole, expectedAuthority: String) {
        givenKnownUser(platformAdmin = false)
        givenMembership(tenantA, role)
        TenantContext.set(tenantA)

        val auth = convert(jwt())

        assertEquals(setOf(expectedAuthority), authorities(auth))
        assertEquals(user.id, auth.userId)
    }

    @Test
    fun `user without membership in the requested tenant gets no role`() {
        givenKnownUser(platformAdmin = false)
        givenNoMembership(tenantA)
        TenantContext.set(tenantA)

        val auth = convert(jwt())

        assertTrue(authorities(auth).isEmpty())   // -> 403 on /api/**: no cross-tenant access
        assertEquals(user.id, auth.userId)        // still a known user, e.g. for /api/me
        verify(exactly = 1) { memberships.findByIdUserIdAndIdTenantId(user.id, tenantA.id) }
    }

    @Test
    fun `role of another tenant does not bleed into the requested tenant`() {
        givenKnownUser(platformAdmin = false)
        givenMembership(tenantA, UserTenantRole.TENANT_ADMIN)   // admin of A ...
        givenMembership(tenantB, UserTenantRole.STAFF)          // ... but only staff in B
        TenantContext.set(tenantB)

        val auth = convert(jwt())

        assertEquals(setOf("ROLE_STAFF"), authorities(auth))
    }

    @Test
    fun `without tenant context no membership is looked up and no tenant role is granted`() {
        givenKnownUser(platformAdmin = false)

        val auth = convert(jwt())   // e.g. root domain: /api/me, /api/signup/**

        assertTrue(authorities(auth).isEmpty())
        assertEquals(user.id, auth.userId)
        verify(exactly = 0) { memberships.findByIdUserIdAndIdTenantId(any(), any()) }
    }

    // ------------------------------------------------------------------ platform admin

    @Test
    fun `platform admin without tenant context gets only the platform role`() {
        givenKnownUser(platformAdmin = true)

        val auth = convert(jwt())

        assertEquals(setOf("ROLE_PLATFORM_ADMIN"), authorities(auth))
    }

    @Test
    fun `platform admin who is a member gets both roles`() {
        givenKnownUser(platformAdmin = true)
        givenMembership(tenantA, UserTenantRole.STAFF)
        TenantContext.set(tenantA)

        val auth = convert(jwt())

        assertEquals(setOf("ROLE_STAFF", "ROLE_PLATFORM_ADMIN"), authorities(auth))
    }

    @Test
    fun `platform admin without membership gets no tenant role`() {
        givenKnownUser(platformAdmin = true)
        givenNoMembership(tenantA)
        TenantContext.set(tenantA)

        val auth = convert(jwt())

        assertEquals(setOf("ROLE_PLATFORM_ADMIN"), authorities(auth))   // platform role is not a tenant role
    }

    // ------------------------------------------------------------------ unknown / disabled users

    @Test
    fun `unknown user gets no authorities and no user id`() {
        every { users.findByIdentityIssuerAndIdentitySubject(ISSUER, SUBJECT) } returns null
        TenantContext.set(tenantA)

        val auth = convert(jwt())

        assertTrue(authorities(auth).isEmpty())
        assertNull(auth.userId)   // /api/me answers 404 -> frontend shows the signup screen
        verify(exactly = 0) { memberships.findByIdUserIdAndIdTenantId(any(), any()) }
        verify(exactly = 0) { platformUsers.existsById(any()) }
    }

    @Test
    fun `user is identified by issuer and subject together`() {
        every { users.findByIdentityIssuerAndIdentitySubject(OTHER_ISSUER, SUBJECT) } returns null

        val auth = convert(jwt(issuer = OTHER_ISSUER))   // same sub, different IdP

        assertNull(auth.userId)
        verify(exactly = 1) { users.findByIdentityIssuerAndIdentitySubject(OTHER_ISSUER, SUBJECT) }
    }

    @Test
    fun `disabled user is rejected`() {
        every { users.findByIdentityIssuerAndIdentitySubject(ISSUER, SUBJECT) } returns
                user(status = UserStatus.DISABLED)
        TenantContext.set(tenantA)

        assertThrows<DisabledException> { converter.convert(jwt()) }

        verify(exactly = 0) { memberships.findByIdUserIdAndIdTenantId(any(), any()) }
        verify(exactly = 0) { platformUsers.existsById(any()) }
    }

    // ------------------------------------------------------------------ result

    @Test
    fun `result is authenticated and carries the original token`() {
        givenKnownUser(platformAdmin = false)
        val token = jwt()

        val auth = convert(token)

        assertTrue(auth.isAuthenticated)
        assertEquals(token, auth.token)
    }

    // ------------------------------------------------------------------ helpers

    private fun convert(jwt: Jwt): AppAuthentication = converter.convert(jwt) as AppAuthentication

    private fun authorities(auth: AppAuthentication): Set<String?> = auth.authorities.map { it.authority }.toSet()

    private fun givenKnownUser(platformAdmin: Boolean) {
        every { users.findByIdentityIssuerAndIdentitySubject(ISSUER, SUBJECT) } returns user
        every { platformUsers.existsById(user.id) } returns platformAdmin
    }

    private fun givenMembership(tenant: TenantInfo, role: UserTenantRole) {
        every { memberships.findByIdUserIdAndIdTenantId(user.id, tenant.id) } returns
                UserTenant(UserTenantId(user.id, tenant.id), role)
    }

    private fun givenNoMembership(tenant: TenantInfo) {
        every { memberships.findByIdUserIdAndIdTenantId(user.id, tenant.id) } returns null
    }

    private fun jwt(issuer: String = ISSUER, subject: String = SUBJECT): Jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .issuer(issuer)
            .subject(subject)
            .build()

    private companion object {
        const val ISSUER = "http://localhost:8081/realms/reisebus-dev"
        const val OTHER_ISSUER = "https://login.other-idp.example.com"
        const val SUBJECT = "kc-sub-1"

        fun user(status: UserStatus = UserStatus.ACTIVE) = User(
            id = UUID.randomUUID(),
            email = "user@example.com",
            identityIssuer = ISSUER,
            identitySubject = SUBJECT,
            firstName = "Test",
            lastName = "User",
            status = status,
        )
    }
}
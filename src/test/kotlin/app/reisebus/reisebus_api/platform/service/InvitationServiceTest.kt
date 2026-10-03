package app.reisebus.reisebus_api.platform.service

import app.reisebus.reisebus_api.common.context.InvitableRole
import app.reisebus.reisebus_api.platform.domain.*
import app.reisebus.reisebus_api.platform.persistence.InvitationRepository
import app.reisebus.reisebus_api.platform.persistence.TenantRepository
import app.reisebus.reisebus_api.platform.persistence.UserRepository
import app.reisebus.reisebus_api.platform.persistence.UserTenantRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.*

/**
 * Plain unit test, no Spring context. Covers the rules of the invitation flow.
 * Not visible here (needs the integration level): transaction rollback, dirty checking of
 * `acceptedAt`, and the pessimistic lock on findByTokenHash.
 */
class InvitationServiceTest {

    private val invitations = mockk<InvitationRepository>()
    private val tenants = mockk<TenantRepository>()
    private val users = mockk<UserRepository>()
    private val memberships = mockk<UserTenantRepository>()
    private val userProvisioning = mockk<UserProvisioningService>()
    private val mailer = mockk<InvitationMailer>()

    private val service = InvitationService(
        invitations, tenants, users, memberships, userProvisioning, mailer,
        baseDomain = "app.example.com",
        scheme = "https",
    )

    private val tenantId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val tenant = Tenant(
        id = tenantId,
        companyName = "Acme Tours",
        slug = "acme",
        schemaName = "tenant_acme_0123456789ab",
        status = TenantStatus.ACTIVE,
    )
    private val user = mockk<User> { every { id } returns userId }

    private val savedInvitations = mutableListOf<Invitation>()
    private val savedMemberships = mutableListOf<UserTenant>()
    private val sentUrls = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        every { tenants.findById(tenantId) } returns Optional.of(tenant)
        every { users.findByEmail(any()) } returns null
        every { memberships.existsByIdUserIdAndIdTenantId(userId, tenantId) } returns false
        every { invitations.save(any()) } answers { firstArg<Invitation>().also { savedInvitations += it } }
        every { memberships.save(any()) } answers { firstArg<UserTenant>().also { savedMemberships += it } }
        every { userProvisioning.getOrCreate(any()) } returns user
        every { mailer.send(any(), any(), any(), capture(sentUrls)) } just Runs
    }

    // ================================================================== sha256Hex

    @Test
    fun `sha256Hex matches the known test vector`() {
        // The service relies on this function for storing and looking up tokens.
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex("abc"))
    }

    // ================================================================== invite

    @Test
    fun `invite stores a normalized, unaccepted invitation that expires in seven days`() {
        val before = Instant.now()

        service.invite(tenantId, "  Driver@Example.COM ", InvitableRole.DRIVER)

        val after = Instant.now()
        val saved = savedInvitations.single()
        assertEquals(tenantId, saved.tenantId)
        assertEquals("driver@example.com", saved.email)
        assertEquals(UserTenantRole.DRIVER, saved.role)
        assertNull(saved.acceptedAt)
        assertTrue(!saved.expiresAt.isBefore(before.plus(7, ChronoUnit.DAYS)))
        assertTrue(!saved.expiresAt.isAfter(after.plus(7, ChronoUnit.DAYS)))
    }

    @Test
    fun `invite maps each invitable role`() {
        service.invite(tenantId, "a@example.com", InvitableRole.STAFF)
        service.invite(tenantId, "b@example.com", InvitableRole.DRIVER)

        assertEquals(listOf(UserTenantRole.STAFF, UserTenantRole.DRIVER), savedInvitations.map { it.role })
    }

    @Test
    fun `invite stores only the hash of the token that is mailed`() {
        service.invite(tenantId, "driver@example.com", InvitableRole.DRIVER)

        val saved = savedInvitations.single()
        val url = sentUrls.single()
        val rawToken = url.substringAfter("?token=")

        assertTrue(url.startsWith("https://acme.app.example.com/accept-invitation?token="))
        assertEquals(sha256Hex(rawToken), saved.tokenHash)   // the mailed token hashes to the stored value
        assertNotEquals(rawToken, saved.tokenHash)            // the raw token itself is never stored
        assertEquals(64, saved.tokenHash.length)              // fits invitation.token_hash VARCHAR(64)
    }

    @Test
    fun `invite generates a url-safe token with 256 bit of randomness`() {
        service.invite(tenantId, "driver@example.com", InvitableRole.DRIVER)

        val rawToken = sentUrls.single().substringAfter("?token=")
        assertEquals(43, rawToken.length)                     // 32 bytes, base64url without padding
        assertTrue(Regex("^[A-Za-z0-9_-]+$").matches(rawToken))
    }

    @Test
    fun `invite generates a different token every time`() {
        service.invite(tenantId, "driver@example.com", InvitableRole.DRIVER)
        service.invite(tenantId, "driver@example.com", InvitableRole.DRIVER)

        assertEquals(2, sentUrls.map { it.substringAfter("?token=") }.toSet().size)
        assertEquals(2, savedInvitations.map { it.tokenHash }.toSet().size)
    }

    @Test
    fun `invite mails the normalized address with company, role and link`() {
        service.invite(tenantId, "Driver@Example.com", InvitableRole.DRIVER)

        verify(exactly = 1) {
            mailer.send(
                email = "driver@example.com",
                companyName = "Acme Tours",
                role = InvitableRole.DRIVER.role.value,
                acceptUrl = any(),
            )
        }
    }

    @Test
    fun `invite saves the invitation before it sends the mail`() {
        service.invite(tenantId, "driver@example.com", InvitableRole.DRIVER)

        verifyOrder {
            invitations.save(any())
            mailer.send(any(), any(), any(), any())
        }
    }

    @Test
    fun `invite looks up an existing user by the normalized email`() {
        service.invite(tenantId, "  Driver@Example.COM ", InvitableRole.DRIVER)

        verify(exactly = 1) { users.findByEmail("driver@example.com") }
    }

    @Test
    fun `invite for an existing member returns 409 and neither saves nor mails`() {
        every { users.findByEmail("driver@example.com") } returns user
        every { memberships.existsByIdUserIdAndIdTenantId(userId, tenantId) } returns true

        val exception = assertThrows<ResponseStatusException> {
            service.invite(tenantId, "driver@example.com", InvitableRole.DRIVER)
        }

        assertEquals(409, exception.statusCode.value())
        assertTrue(savedInvitations.isEmpty())
        verify(exactly = 0) { mailer.send(any(), any(), any(), any()) }
    }

    @Test
    fun `invite for an existing user of another tenant is allowed`() {
        // a driver can work for several companies
        every { users.findByEmail("driver@example.com") } returns user
        every { memberships.existsByIdUserIdAndIdTenantId(userId, tenantId) } returns false

        service.invite(tenantId, "driver@example.com", InvitableRole.DRIVER)

        assertEquals(1, savedInvitations.size)
        assertEquals(1, sentUrls.size)
    }

    @Test
    fun `invite for an unknown tenant fails and neither saves nor mails`() {
        every { tenants.findById(tenantId) } returns Optional.empty()

        assertThrows<TenantNotFoundException> {
            service.invite(tenantId, "driver@example.com", InvitableRole.DRIVER)
        }

        assertTrue(savedInvitations.isEmpty())
        verify(exactly = 0) { mailer.send(any(), any(), any(), any()) }
    }

    @Test
    fun `invite propagates a mail failure so the transaction can roll back`() {
        every { mailer.send(any(), any(), any(), any()) } throws IllegalStateException("smtp down")

        assertThrows<IllegalStateException> {
            service.invite(tenantId, "driver@example.com", InvitableRole.DRIVER)
        }
    }

    // ================================================================== accept

    @Test
    fun `accept adds the membership with the invited role and marks the invitation accepted`() {
        val invitation = givenInvitation(role = UserTenantRole.STAFF)
        val before = Instant.now()

        val result = service.accept(jwt(), RAW_TOKEN)

        val membership = savedMemberships.single()
        assertEquals(userId, membership.id.userId)
        assertEquals(tenantId, membership.id.tenantId)
        assertEquals(UserTenantRole.STAFF, membership.role)

        val acceptedAt = assertNotNull(invitation.acceptedAt)
        assertTrue(!acceptedAt.isBefore(before))
        assertTrue(!acceptedAt.isAfter(Instant.now()))

        assertEquals(AcceptedInvitation(tenantSlug = "acme", host = "acme.app.example.com"), result)
    }

    @Test
    fun `accept ignores whitespace around the token`() {
        val invitation = givenInvitation()

        service.accept(jwt(), "  $RAW_TOKEN \n")

        assertNotNull(invitation.acceptedAt)
    }

    @Test
    fun `accept compares the email case-insensitively`() {
        val invitation = givenInvitation(email = "driver@example.com")

        service.accept(jwt(email = "Driver@Example.COM"), RAW_TOKEN)

        assertNotNull(invitation.acceptedAt)
    }

    @Test
    fun `accept for an existing member adds no second membership but still consumes the invitation`() {
        val invitation = givenInvitation()
        every { memberships.existsByIdUserIdAndIdTenantId(userId, tenantId) } returns true

        service.accept(jwt(), RAW_TOKEN)

        assertTrue(savedMemberships.isEmpty())
        assertNotNull(invitation.acceptedAt)
    }

    @Test
    fun `accept with an unknown token returns 410 and creates nothing`() {
        every { invitations.findByTokenHash(any()) } returns null

        val exception = assertThrows<ResponseStatusException> { service.accept(jwt(), RAW_TOKEN) }

        assertEquals(410, exception.statusCode.value())
        verify(exactly = 0) { userProvisioning.getOrCreate(any()) }
        assertTrue(savedMemberships.isEmpty())
    }

    @Test
    fun `accept with an already used token returns 410 and creates nothing`() {
        val usedAt = Instant.now().minusSeconds(60)
        val invitation = givenInvitation(acceptedAt = usedAt)

        val exception = assertThrows<ResponseStatusException> { service.accept(jwt(), RAW_TOKEN) }

        assertEquals(410, exception.statusCode.value())
        assertEquals(usedAt, invitation.acceptedAt)   // unchanged
        verify(exactly = 0) { userProvisioning.getOrCreate(any()) }
        assertTrue(savedMemberships.isEmpty())
    }

    @Test
    fun `accept with an expired token returns 410 and creates nothing`() {
        val invitation = givenInvitation(expiresAt = Instant.now().minusSeconds(60))

        val exception = assertThrows<ResponseStatusException> { service.accept(jwt(), RAW_TOKEN) }

        assertEquals(410, exception.statusCode.value())
        assertNull(invitation.acceptedAt)
        verify(exactly = 0) { userProvisioning.getOrCreate(any()) }
        assertTrue(savedMemberships.isEmpty())
    }

    @Test
    fun `unknown, used and expired tokens are indistinguishable for the caller`() {
        every { invitations.findByTokenHash(any()) } returns null
        val unknown = assertThrows<ResponseStatusException> { service.accept(jwt(), RAW_TOKEN) }

        givenInvitation(acceptedAt = Instant.now().minusSeconds(60))
        val used = assertThrows<ResponseStatusException> { service.accept(jwt(), RAW_TOKEN) }

        givenInvitation(expiresAt = Instant.now().minusSeconds(60))
        val expired = assertThrows<ResponseStatusException> { service.accept(jwt(), RAW_TOKEN) }

        // no oracle: an attacker cannot tell which tokens exist
        listOf(used, expired).forEach {
            assertEquals(unknown.statusCode.value(), it.statusCode.value())
            assertEquals(unknown.reason, it.reason)
        }
    }

    @Test
    fun `accept with a different email returns 403 and creates nothing`() {
        val invitation = givenInvitation(email = "driver@example.com")

        val exception = assertThrows<ResponseStatusException> {
            service.accept(jwt(email = "someone-else@example.com"), RAW_TOKEN)
        }

        assertEquals(403, exception.statusCode.value())
        assertNull(invitation.acceptedAt)                 // token not burned by the wrong person
        verify(exactly = 0) { userProvisioning.getOrCreate(any()) }
        assertTrue(savedMemberships.isEmpty())
    }

    @Test
    fun `accept with an unverified email returns 403 and creates nothing`() {
        val invitation = givenInvitation()

        val exception = assertThrows<ResponseStatusException> {
            service.accept(jwt(emailVerified = false), RAW_TOKEN)
        }

        assertEquals(403, exception.statusCode.value())
        assertNull(invitation.acceptedAt)
        verify(exactly = 0) { userProvisioning.getOrCreate(any()) }
        assertTrue(savedMemberships.isEmpty())
    }

    @Test
    fun `accept with a token that has no email claim returns 403`() {
        givenInvitation()

        val exception = assertThrows<ResponseStatusException> {
            service.accept(jwt(email = null), RAW_TOKEN)
        }

        assertEquals(403, exception.statusCode.value())
        verify(exactly = 0) { userProvisioning.getOrCreate(any()) }
    }

    @ParameterizedTest
    @EnumSource(TenantStatus::class, mode = EnumSource.Mode.EXCLUDE, names = ["ACTIVE"])
    fun `accept for a tenant that is not active returns 410 and creates nothing`(status: TenantStatus) {
        val invitation = givenInvitation()
        tenant.status = status

        val exception = assertThrows<ResponseStatusException> { service.accept(jwt(), RAW_TOKEN) }

        assertEquals(410, exception.statusCode.value())
        assertNull(invitation.acceptedAt)
        verify(exactly = 0) { userProvisioning.getOrCreate(any()) }
        assertTrue(savedMemberships.isEmpty())
    }

    @Test
    fun `accept for a tenant that no longer exists returns 410`() {
        givenInvitation()
        every { tenants.findById(tenantId) } returns Optional.empty()

        val exception = assertThrows<ResponseStatusException> { service.accept(jwt(), RAW_TOKEN) }

        assertEquals(410, exception.statusCode.value())
        verify(exactly = 0) { userProvisioning.getOrCreate(any()) }
    }

    @Test
    fun `accept does not consume the invitation when the user cannot be provisioned`() {
        val invitation = givenInvitation()
        every { userProvisioning.getOrCreate(any()) } throws
                ResponseStatusException(HttpStatus.CONFLICT, "This email is already registered with another identity")

        val exception = assertThrows<ResponseStatusException> { service.accept(jwt(), RAW_TOKEN) }

        assertEquals(409, exception.statusCode.value())
        assertNull(invitation.acceptedAt)
        assertTrue(savedMemberships.isEmpty())
    }

    // ================================================================== helpers

    /** Registers an invitation that belongs to RAW_TOKEN (looked up by its hash). */
    private fun givenInvitation(
        email: String = "driver@example.com",
        role: UserTenantRole = UserTenantRole.DRIVER,
        acceptedAt: Instant? = null,
        expiresAt: Instant = Instant.now().plus(1, ChronoUnit.DAYS),
    ): Invitation {
        val invitation = Invitation(
            id = UUID.randomUUID(),
            tenantId = tenantId,
            email = email,
            role = role,
            tokenHash = sha256Hex(RAW_TOKEN),
            expiresAt = expiresAt,
            acceptedAt = acceptedAt,
        )
        every { invitations.findByTokenHash(sha256Hex(RAW_TOKEN)) } returns invitation
        return invitation
    }

    private fun jwt(email: String? = "driver@example.com", emailVerified: Boolean? = true): Jwt {
        val builder = Jwt.withTokenValue("token").header("alg", "none").subject("kc-sub")
        email?.let { builder.claim("email", it) }
        emailVerified?.let { builder.claim("email_verified", it) }
        return builder.build()
    }

    private companion object {
        const val RAW_TOKEN = "raw-invitation-token"
    }
}
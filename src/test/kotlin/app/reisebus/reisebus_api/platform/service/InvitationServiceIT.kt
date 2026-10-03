package app.reisebus.reisebus_api.platform.service

import app.reisebus.reisebus_api.common.context.InvitableRole
import app.reisebus.reisebus_api.platform.TestcontainersConfiguration
import app.reisebus.reisebus_api.platform.domain.Tenant
import app.reisebus.reisebus_api.platform.domain.TenantStatus
import app.reisebus.reisebus_api.platform.persistence.TenantRepository
import com.ninjasquad.springmockk.MockkBean
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.server.ResponseStatusException
import java.sql.Timestamp
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Real Postgres (Testcontainers), real repositories, real transactions. The test class is NOT
 * @Transactional on purpose: every service call commits, so rollback and locking behave as in production.
 *
 * Proves what the unit test cannot: rollback on mail failure, persistence of `accepted_at`
 * (JPA dirty checking), the row lock on findByTokenHash, and the DB constraints.
 * Only the mailer is mocked, to capture the raw token that would be mailed.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class InvitationServiceIT(
    @Autowired private val invitationService: InvitationService,
    @Autowired private val tenantRepository: TenantRepository,
    @Autowired private val jdbc: JdbcTemplate,
) {
    @MockkBean
    private lateinit var mailer: InvitationMailer

    private val mailedUrls = mutableListOf<String>()
    private val createdTenantIds = mutableListOf<UUID>()

    @BeforeEach
    fun setUp() {
        every { mailer.send(any(), any(), any(), capture(mailedUrls)) } just Runs
    }

    @AfterEach
    fun cleanUp() {
        // Only rows created by this test: other tests may share the same database.
        createdTenantIds.forEach { id ->
            jdbc.update("DELETE FROM platform.invitation WHERE tenant_id = ?", id)
            jdbc.update("DELETE FROM platform.user_tenants WHERE tenant_id = ?", id)
            jdbc.update("DELETE FROM platform.tenant WHERE id = ?", id)
        }
        jdbc.update("DELETE FROM platform.users WHERE email LIKE ?", "%@$EMAIL_DOMAIN")
        createdTenantIds.clear()
        mailedUrls.clear()
    }

    // ------------------------------------------------------------------ invite

    @Test
    fun `invite persists a pending invitation that stores only the token hash`() {
        val tenant = createActiveTenant("invite")
        val email = uniqueEmail()

        val token = inviteAndGetToken(tenant, email, InvitableRole.DRIVER)

        val row = jdbc.queryForMap(
            "SELECT email, role, token_hash, accepted_at FROM platform.invitation WHERE tenant_id = ?",
            tenant.id,
        )
        assertEquals(email, row["email"])
        assertEquals("driver", (row["role"] as String).lowercase())   // passes for 'driver' and 'DRIVER'
        assertEquals(sha256Hex(token), row["token_hash"])              // hash of the mailed token
        assertTrue(row["token_hash"] != token)                         // raw token not in the database
        assertNull(row["accepted_at"])
    }

    @Test
    fun `invite rolls back the invitation when the mail cannot be sent`() {
        val tenant = createActiveTenant("rollback")
        every { mailer.send(any(), any(), any(), any()) } throws IllegalStateException("smtp down")

        assertThrows<IllegalStateException> {
            invitationService.invite(tenant.id, uniqueEmail(), InvitableRole.DRIVER)
        }

        // no invitation without a delivered mail
        assertEquals(0, invitationCount(tenant.id))
    }

    // ------------------------------------------------------------------ accept

    @Test
    fun `accept creates the user and the membership and persists accepted_at`() {
        val tenant = createActiveTenant("accept")
        val email = uniqueEmail()
        val token = inviteAndGetToken(tenant, email, InvitableRole.STAFF)

        val result = invitationService.accept(jwt(email), token)

        assertEquals(tenant.slug, result.tenantSlug)
        assertEquals(1, userCount(email))
        assertEquals(1, membershipCount(email, tenant.id))
        // dirty checking flushed the change on commit:
        val acceptedAt = jdbc.queryForObject(
            "SELECT accepted_at FROM platform.invitation WHERE tenant_id = ?",
            Timestamp::class.java,
            tenant.id,
        )
        assertNotNull(acceptedAt)
    }

    @Test
    fun `a used token cannot be used a second time`() {
        val tenant = createActiveTenant("reuse")
        val email = uniqueEmail()
        val token = inviteAndGetToken(tenant, email, InvitableRole.DRIVER)
        invitationService.accept(jwt(email), token)

        val exception = assertThrows<ResponseStatusException> { invitationService.accept(jwt(email), token) }

        assertEquals(410, exception.statusCode.value())
        assertEquals(1, membershipCount(email, tenant.id))
    }

    @Test
    fun `a driver can accept invitations from two tenants with one account`() {
        val tenantA = createActiveTenant("multi-a")
        val tenantB = createActiveTenant("multi-b")
        val email = uniqueEmail()
        val identity = jwt(email)   // same issuer + subject for both accepts

        invitationService.accept(identity, inviteAndGetToken(tenantA, email, InvitableRole.DRIVER))
        invitationService.accept(identity, inviteAndGetToken(tenantB, email, InvitableRole.DRIVER))

        assertEquals(1, userCount(email))                   // one user ...
        assertEquals(1, membershipCount(email, tenantA.id)) // ... two memberships
        assertEquals(1, membershipCount(email, tenantB.id))
    }

    @Test
    fun `an invitation for a tenant that is not active stays unused`() {
        val tenant = createActiveTenant("inactive")
        val email = uniqueEmail()
        val token = inviteAndGetToken(tenant, email, InvitableRole.DRIVER)
        jdbc.update("UPDATE platform.tenant SET status = ? WHERE id = ?", TenantStatus.FAILED.name, tenant.id)

        val exception = assertThrows<ResponseStatusException> { invitationService.accept(jwt(email), token) }

        assertEquals(410, exception.statusCode.value())
        assertEquals(0, membershipCount(email, tenant.id))
        assertEquals(0, userCount(email))
    }

    @Test
    fun `two concurrent accepts of the same token succeed exactly once`() {
        val tenant = createActiveTenant("race")
        val email = uniqueEmail()
        val token = inviteAndGetToken(tenant, email, InvitableRole.DRIVER)
        val identity = jwt(email)

        val barrier = CyclicBarrier(2)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val futures = (1..2).map {
                pool.submit(Callable {
                    barrier.await(10, TimeUnit.SECONDS)   // start both calls at the same moment
                    runCatching { invitationService.accept(identity, token) }
                })
            }
            val results = futures.map { it.get(30, TimeUnit.SECONDS) }

            // The row lock (PESSIMISTIC_WRITE) makes the second call wait, then see accepted_at and fail with 410.
            assertEquals(1, results.count { it.isSuccess })
            val failure = results.single { it.isFailure }.exceptionOrNull()
            assertTrue(failure is ResponseStatusException, "expected 410 but was $failure")
            assertEquals(410, failure.statusCode.value())

            assertEquals(1, userCount(email))
            assertEquals(1, membershipCount(email, tenant.id))
        } finally {
            pool.shutdownNow()
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun createActiveTenant(slugPrefix: String): Tenant {
        val id = UUID.randomUUID()
        val slug = "$slugPrefix-${id.toString().take(8)}"
        val schema = "tenant_${slug}_${id.toString().replace("-", "").take(12)}".replace("-", "_")
        val tenant = tenantRepository.save(
            Tenant(
                id = id,
                companyName = "IT $slug",
                slug = slug,
                schemaName = schema,
                status = TenantStatus.ACTIVE,   // saved directly: no TenantCreated event, no provisioning
            )
        )
        createdTenantIds += id
        return tenant
    }

    /** Invites through the real service and returns the raw token the mailer was given. */
    private fun inviteAndGetToken(tenant: Tenant, email: String, role: InvitableRole): String {
        invitationService.invite(tenant.id, email, role)
        return mailedUrls.last().substringAfter("?token=")
    }

    private fun uniqueEmail() = "it-${UUID.randomUUID().toString().take(8)}@$EMAIL_DOMAIN"

    private fun jwt(email: String): Jwt = Jwt.withTokenValue("token")
        .header("alg", "none")
        .issuer(ISSUER)
        .subject("sub-${UUID.randomUUID()}")
        .claim("email", email)
        .claim("email_verified", true)
        .claim("given_name", "Test")
        .claim("family_name", "Driver")
        .build()

    private fun invitationCount(tenantId: UUID): Int = count(
        "SELECT count(*) FROM platform.invitation WHERE tenant_id = ?", tenantId,
    )

    private fun userCount(email: String): Int = count(
        "SELECT count(*) FROM platform.users WHERE email = ?", email,
    )

    private fun membershipCount(email: String, tenantId: UUID): Int = count(
        """SELECT count(*) FROM platform.user_tenants ut
           JOIN platform.users u ON u.id = ut.user_id
           WHERE u.email = ? AND ut.tenant_id = ?""",
        email, tenantId,
    )

    private fun count(sql: String, vararg args: Any): Int = jdbc.queryForObject(sql, Int::class.java, *args) ?: 0

    private companion object {
        const val ISSUER = "http://localhost:8081/realms/reisebus-dev"
        const val EMAIL_DOMAIN = "invitation-it.example.com"
    }
}
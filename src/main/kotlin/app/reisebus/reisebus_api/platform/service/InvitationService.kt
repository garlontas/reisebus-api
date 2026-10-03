package app.reisebus.reisebus_api.platform.service

import app.reisebus.reisebus_api.common.context.InvitableRole
import app.reisebus.reisebus_api.common.tenancy.verifiedEmail
import app.reisebus.reisebus_api.platform.domain.*
import app.reisebus.reisebus_api.platform.persistence.InvitationRepository
import app.reisebus.reisebus_api.platform.persistence.TenantRepository
import app.reisebus.reisebus_api.platform.persistence.UserRepository
import app.reisebus.reisebus_api.platform.persistence.UserTenantRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*


data class AcceptedInvitation(val tenantSlug: String, val host: String)

internal fun sha256Hex(value: String): String =
    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))

@Service
class InvitationService(
    private val invitations: InvitationRepository,
    private val tenants: TenantRepository,
    private val users: UserRepository,
    private val memberships: UserTenantRepository,
    private val userProvisioning: UserProvisioningService,
    private val mailer: InvitationMailer,
    @Value("\${app.base-domain}") private val baseDomain: String,
    @Value("\${app.scheme:https}") private val scheme: String,
) {
    private val random = SecureRandom()

    @Transactional
    fun invite(tenantId: UUID, email: String, role: InvitableRole) {
        val tenant = tenants.findById(tenantId).orElseThrow { TenantNotFoundException(tenantId) }
        val normalizedEmail = email.trim().lowercase()

        users.findByEmail(normalizedEmail)?.let {
            if (memberships.existsByIdUserIdAndIdTenantId(it.id, tenantId)) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "User is already a member of this tenant")
            }
        }

        val rawToken = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(32).also { random.nextBytes(it) })

        invitations.save(
            Invitation(
                id = UUID.randomUUID(),
                tenantId = tenantId,
                email = normalizedEmail,
                role = UserTenantRole.valueOf(role.role.name),
                tokenHash = sha256Hex(rawToken),          // only the hash is stored
                expiresAt = Instant.now().plus(7, ChronoUnit.DAYS),
            )
        )
        mailer.send(
            email = normalizedEmail,
            companyName = tenant.companyName,
            role = role.role.value,
            acceptUrl = "$scheme://${tenant.slug}.$baseDomain/accept-invitation?token=$rawToken",
        )
    }

    @Transactional
    fun accept(jwt: Jwt, rawToken: String): AcceptedInvitation {
        // Same generic error for unknown/used/expired: no token oracle.
        val gone = ResponseStatusException(HttpStatus.GONE, "Invitation is invalid or expired")
        val invitation = invitations.findByTokenHash(sha256Hex(rawToken.trim())) ?: throw gone
        val now = Instant.now()
        if (invitation.acceptedAt != null || invitation.expiresAt.isBefore(now)) throw gone

        // The invitation belongs to one mailbox: the verified token email must match.
        if (jwt.verifiedEmail() != invitation.email.lowercase()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Invitation was issued for a different email address")
        }

        val tenant = tenants.findById(invitation.tenantId).orElseThrow { gone }
        if (tenant.status != TenantStatus.ACTIVE) throw gone

        val user = userProvisioning.getOrCreate(jwt)
        // A driver can work for several companies: existing users only get an additional membership.
        if (!memberships.existsByIdUserIdAndIdTenantId(user.id, tenant.id)) {
            memberships.save(UserTenant(UserTenantId(user.id, tenant.id), invitation.role))
        }
        invitation.acceptedAt = now
        return AcceptedInvitation(tenant.slug, "${tenant.slug}.$baseDomain")
    }
}
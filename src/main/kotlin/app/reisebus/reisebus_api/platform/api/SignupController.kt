package app.reisebus.reisebus_api.platform.api

import app.reisebus.reisebus_api.platform.api.contract.*
import app.reisebus.reisebus_api.platform.messaging.CreateTenant
import app.reisebus.reisebus_api.platform.security.AppAuthentication
import app.reisebus.reisebus_api.platform.service.AcceptedInvitation
import app.reisebus.reisebus_api.platform.service.InvitationService
import app.reisebus.reisebus_api.platform.service.PlatformService
import app.reisebus.reisebus_api.platform.service.SignupService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.util.*


/**
 * Everything under /api/signup needs a valid IdP token but NOT a local user yet
 * (see SecurityConfig: authenticated(), no role).
 */
@RestController
@RequestMapping("/api/signup")
class SignupController(
    private val signupService: SignupService,
    private val platformService: PlatformService,
    private val invitationService: InvitationService,
) {

    @GetMapping("/slug-availability")
    fun slugAvailability(@RequestParam slug: String): SlugAvailabilityResponse =
        SlugAvailabilityResponse(slug.trim().lowercase(), platformService.isDesiredSlugAvailable(slug))

    @PostMapping("/tenant")
    fun signupTenant(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody @Valid request: CreateTenantRequest,
    ): ResponseEntity<CreateTenantResponse> {
        val tenant = signupService.registerTenant(jwt, CreateTenant(request.companyName, request.desiredSlug))
        return ResponseEntity
            .created(URI.create("/api/signup/tenants/${tenant.tenantId}"))
            .body(CreateTenantResponse(id = tenant.tenantId, status = tenant.status.name))
    }

    /** Poll until status = ACTIVE, then redirect to <slug>.<base-domain>. */
    @GetMapping("/tenants/{id}")
    fun provisioningStatus(auth: AppAuthentication, @PathVariable id: UUID): SignupProvisioningStatus {
        val userId = auth.userId ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return signupService.provisioningStatus(userId, id)
    }

    @PostMapping("/accept-invitation")
    fun acceptInvitation(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody @Valid request: AcceptInvitationRequest,
    ): AcceptedInvitation = invitationService.accept(jwt, request.token)
}

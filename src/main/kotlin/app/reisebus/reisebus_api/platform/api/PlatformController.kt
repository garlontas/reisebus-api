package app.reisebus.reisebus_api.platform.api

import app.reisebus.reisebus_api.platform.api.contract.CreateTenantRequest
import app.reisebus.reisebus_api.platform.api.contract.CreateTenantResponse
import app.reisebus.reisebus_api.platform.api.contract.SignupProvisioningStatus
import app.reisebus.reisebus_api.platform.api.contract.SlugAvailabilityResponse
import app.reisebus.reisebus_api.platform.messaging.CreateTenant
import app.reisebus.reisebus_api.platform.service.PlatformService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI
import java.util.*

@RestController
@RequestMapping("/api/platform")
class PlatformController(
    private val platformService: PlatformService
) {

    @PostMapping("/signups")
    fun createTenant(@RequestBody @Valid request: CreateTenantRequest): ResponseEntity<CreateTenantResponse> {
        val tenant = platformService.createTenant(
            CreateTenant(request.companyName, request.desiredSlug)
        )
        return ResponseEntity
            .created(URI.create("/api/platform/signups/${tenant.tenantId}"))
            .body(CreateTenantResponse(id = tenant.tenantId, status = tenant.status.name))
    }

    @GetMapping("/signups/{id}")
    fun getSignupProvisioningStatus(@PathVariable id: UUID): SignupProvisioningStatus {
        return platformService.getSignupProvisioningStatus(id)
    }

    @GetMapping("/slug-availability")
    fun checkShopSlugAvailability(@RequestParam("slug") slug: String): SlugAvailabilityResponse {
        val isAvailable = platformService.isDesiredSlugAvailable(slug)
        return SlugAvailabilityResponse(slug.trim().lowercase(), isAvailable)
    }
}

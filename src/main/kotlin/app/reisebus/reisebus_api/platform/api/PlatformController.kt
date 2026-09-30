package app.reisebus.reisebus_api.platform.api

import app.reisebus.reisebus_api.platform.service.TenantService
import app.reisebus.reisebus_api.platform.api.contract.CreateTenantRequest
import app.reisebus.reisebus_api.platform.api.contract.CreateTenantResponse
import app.reisebus.reisebus_api.platform.api.contract.SignupProvisioningStatus
import app.reisebus.reisebus_api.platform.messaging.CreateTenant
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/api/platform")
class PlatformController(
    private val tenantService: TenantService
) {

    @PostMapping("/signups")
    fun createTenant(@RequestBody @Valid request: CreateTenantRequest): ResponseEntity<CreateTenantResponse> {
        val tenant = tenantService.createTenant(
            CreateTenant(request.companyName, request.desiredSlug)
        )
        return ResponseEntity
            .created(URI.create("/api/platform/signups/${tenant.tenantId}"))
            .body(CreateTenantResponse(id = tenant.tenantId, status = tenant.status.name))
    }

    @GetMapping("/signups/{id}")
    fun getSignupProvisioningStatus(@PathVariable id: UUID): SignupProvisioningStatus {
        return tenantService.getSignupProvisioningStatus(id)
    }
}

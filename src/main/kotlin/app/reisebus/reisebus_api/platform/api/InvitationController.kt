package app.reisebus.reisebus_api.platform.api

import app.reisebus.reisebus_api.common.context.TenantContext
import app.reisebus.reisebus_api.platform.api.contract.InviteRequest
import app.reisebus.reisebus_api.platform.service.InvitationService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/invitations")
class InvitationController(private val invitationService: InvitationService) {

    @PostMapping
    fun invite(@RequestBody @Valid request: InviteRequest): ResponseEntity<Void> {
        val tenant = TenantContext.get()
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Call this on a tenant host")
        invitationService.invite(tenant.id, request.email, request.role)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }
}

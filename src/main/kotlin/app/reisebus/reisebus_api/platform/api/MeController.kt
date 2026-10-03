package app.reisebus.reisebus_api.platform.api

import app.reisebus.reisebus_api.common.context.TenantContext
import app.reisebus.reisebus_api.platform.security.AppAuthentication
import app.reisebus.reisebus_api.platform.service.MeResponse
import app.reisebus.reisebus_api.platform.service.MeService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/me")
class MeController(private val meService: MeService) {

    @GetMapping
    fun me(auth: AppAuthentication): ResponseEntity<MeResponse> {
        // 404 = valid IdP login but no local user yet -> frontend shows the signup screen
        val userId = auth.userId ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        val platformAdmin = auth.authorities.any { it.authority == "ROLE_PLATFORM_ADMIN" }
        return ResponseEntity.ok(meService.load(userId, TenantContext.get(), platformAdmin))
    }
}

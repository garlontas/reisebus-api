package app.reisebus.reisebus_api.platform.api

import app.reisebus.reisebus_api.platform.service.DuplicateShopSlugException
import app.reisebus.reisebus_api.platform.service.TenantNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class PlatformExceptionHandler {

    @ExceptionHandler(DuplicateShopSlugException::class)
    fun handleDuplicateSlug(e: DuplicateShopSlugException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.message ?: "Shop slug already taken")

    @ExceptionHandler(TenantNotFoundException::class)
    fun handleTenantNotFound(e: TenantNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.message ?: "Tenant not found")

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleInvalidSlug(e: IllegalArgumentException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message ?: "Illegal argument")
}
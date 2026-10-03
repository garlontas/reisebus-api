package app.reisebus.reisebus_api.platform.resolver

import app.reisebus.reisebus_api.common.context.TenantContext
import app.reisebus.reisebus_api.common.context.TenantInfo
import app.reisebus.reisebus_api.platform.domain.TenantStatus
import app.reisebus.reisebus_api.platform.persistence.TenantDomainRepository
import app.reisebus.reisebus_api.platform.persistence.TenantRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.filter.OncePerRequestFilter

@Component
class TenantResolutionFilter(private val resolver: TenantResolver) : OncePerRequestFilter() {
    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        try {
            resolver.resolveByHost(req.serverName)?.let { TenantContext.set(it) }
            chain.doFilter(req, res)
        } finally {
            TenantContext.clear()
        }
    }
}

@Service
class TenantResolver(
    private val domains: TenantDomainRepository,
    private val tenants: TenantRepository,
    @Value("\${app.base-domain}") private val baseDomain: String,   // z. B. app.com
) {
    @Cacheable("tenantByHost", key = "#host.split(':')[0].trim().toLowerCase()", unless = "#result == null")
    fun resolveByHost(host: String): TenantInfo? {
        val normalizedHost = host.substringBefore(':').trim().lowercase()

        // 1. Custom Domain (nur status = ACTIVE)
        domains.findActiveByDomain(normalizedHost)
            ?.tenant?.takeIf { it.status == TenantStatus.ACTIVE }
            ?.let { return TenantInfo(it.id, it.schemaName) }
        // 2. Subdomain-Fallback: acme.app.com → slug "acme"
        if (normalizedHost.endsWith(".$baseDomain".substringBefore(':').trim().lowercase())) {
            val slug = normalizedHost.removeSuffix(".$baseDomain".substringBefore(':').trim().lowercase())
            return tenants.findBySlug(slug)?.takeIf { it.status == TenantStatus.ACTIVE }
                ?.let { TenantInfo(it.id, it.schemaName) }
        }
        return null   // Root-Domain: Signup, /me usw.
    }
}

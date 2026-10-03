package app.reisebus.reisebus_api.platform.resolver

import app.reisebus.reisebus_api.common.context.TenantInfo
import app.reisebus.reisebus_api.platform.domain.Tenant
import app.reisebus.reisebus_api.platform.domain.TenantDomain
import app.reisebus.reisebus_api.platform.domain.TenantStatus
import app.reisebus.reisebus_api.platform.persistence.TenantDomainRepository
import app.reisebus.reisebus_api.platform.persistence.TenantRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Plain unit test (no cache, no Spring): the resolver decides which tenant a request belongs to.
 * The tenant comes ONLY from the host, so every wrong answer here is a cross-tenant risk.
 * Caching is covered in TenantResolverCachingTest.
 */
class TenantResolverTest {

    private val domains = mockk<TenantDomainRepository>()
    private val tenants = mockk<TenantRepository>()
    private val resolver = TenantResolver(domains, tenants, "app.example.com")

    private val acme = tenant("acme")
    private val acmeInfo = TenantInfo(acme.id, acme.schemaName)

    @BeforeEach
    fun setUp() {
        every { domains.findActiveByDomain(any()) } returns null
        every { tenants.findBySlug(any()) } returns null
        every { tenants.findBySlug("acme") } returns acme
    }

    // ------------------------------------------------------------------ subdomain

    @Test
    fun `subdomain of the base domain resolves the tenant by slug`() {
        assertEquals(acmeInfo, resolver.resolveByHost("acme.app.example.com"))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "ACME.APP.EXAMPLE.COM",
            "Acme.App.Example.Com",
            "acme.app.example.com:8443",
            "  acme.app.example.com  ",
        ]
    )
    fun `host is normalized before the lookup`(host: String) {
        assertEquals(acmeInfo, resolver.resolveByHost(host))

        verify(exactly = 1) { domains.findActiveByDomain("acme.app.example.com") }
        verify(exactly = 1) { tenants.findBySlug("acme") }
    }

    @Test
    fun `unknown slug does not resolve`() {
        assertNull(resolver.resolveByHost("ghost.app.example.com"))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "app.example.com",                // root domain: signup, /me
            "evil.com",
            "evilapp.example.com",            // missing dot before the base domain
            "acme.app.example.com.evil.com",  // base domain only in the middle
            "localhost",
        ]
    )
    fun `hosts outside the base domain resolve nothing and are not looked up as slug`(host: String) {
        assertNull(resolver.resolveByHost(host))

        verify(exactly = 0) { tenants.findBySlug(any()) }
    }

    @Test
    fun `nested subdomain does not resolve to the tenant of its last label`() {
        assertNull(resolver.resolveByHost("evil.acme.app.example.com"))

        verify(exactly = 0) { tenants.findBySlug("acme") }   // slug would be "evil.acme", never "acme"
    }

    @Test
    fun `base domain with a port (dev setup) still matches hosts without port`() {
        val devResolver = TenantResolver(domains, tenants, "localhost:8080")

        assertEquals(acmeInfo, devResolver.resolveByHost("acme.localhost"))
        assertEquals(acmeInfo, devResolver.resolveByHost("acme.localhost:8080"))
        assertNull(devResolver.resolveByHost("localhost"))
    }

    // ------------------------------------------------------------------ custom domain

    @Test
    fun `active custom domain resolves the tenant of the domain`() {
        val globex = tenant("globex")
        every { domains.findActiveByDomain("shop.example.org") } returns domainOf(globex)

        assertEquals(TenantInfo(globex.id, globex.schemaName), resolver.resolveByHost("shop.example.org"))
        verify(exactly = 0) { tenants.findBySlug(any()) }   // custom domain wins, no slug lookup
    }

    @Test
    fun `custom domain is looked up in normalized form`() {
        val globex = tenant("globex")
        every { domains.findActiveByDomain("shop.example.org") } returns domainOf(globex)

        assertEquals(TenantInfo(globex.id, globex.schemaName), resolver.resolveByHost("SHOP.Example.org:443"))
    }

    @Test
    fun `host that is neither an active custom domain nor a subdomain resolves nothing`() {
        assertNull(resolver.resolveByHost("unknown.example.org"))
    }

    // ------------------------------------------------------------------ tenant status

    /*
     * These two tests describe the required behaviour and FAIL against the current resolver:
     * it never looks at tenant.status, so PROVISIONING / FAILED tenants resolve (see the fix in the answer).
     */
    @ParameterizedTest
    @EnumSource(TenantStatus::class, mode = EnumSource.Mode.EXCLUDE, names = ["ACTIVE"])
    fun `subdomain of a tenant that is not active does not resolve`(status: TenantStatus) {
        every { tenants.findBySlug("acme") } returns tenant("acme", status)

        assertNull(resolver.resolveByHost("acme.app.example.com"))
    }

    @ParameterizedTest
    @EnumSource(TenantStatus::class, mode = EnumSource.Mode.EXCLUDE, names = ["ACTIVE"])
    fun `custom domain of a tenant that is not active does not resolve`(status: TenantStatus) {
        every { domains.findActiveByDomain("shop.example.org") } returns domainOf(tenant("globex", status))

        assertNull(resolver.resolveByHost("shop.example.org"))
    }

    // ------------------------------------------------------------------ helpers

    private fun tenant(slug: String, status: TenantStatus = TenantStatus.ACTIVE) = Tenant(
        id = UUID.randomUUID(),
        companyName = "Company $slug",
        slug = slug,
        schemaName = "tenant_${slug}_0123456789ab",
        status = status,
    )

    private fun domainOf(owner: Tenant): TenantDomain = mockk { every { tenant } returns owner }
}
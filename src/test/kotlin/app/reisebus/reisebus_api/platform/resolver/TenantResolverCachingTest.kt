package app.reisebus.reisebus_api.platform.resolver

import app.reisebus.reisebus_api.common.context.TenantInfo
import app.reisebus.reisebus_api.platform.domain.Tenant
import app.reisebus.reisebus_api.platform.domain.TenantStatus
import app.reisebus.reisebus_api.platform.persistence.TenantDomainRepository
import app.reisebus.reisebus_api.platform.persistence.TenantRepository
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Minimal Spring context (caching + the real TenantResolver proxy, mocked repositories).
 * A plain unit test cannot see @Cacheable, because there is no proxy without Spring.
 */
@SpringJUnitConfig(TenantResolverCachingTest.Config::class)
@TestPropertySource(properties = ["app.base-domain=app.example.com"])
class TenantResolverCachingTest(
    @Autowired private val resolver: TenantResolver,
    @Autowired private val tenants: TenantRepository,
    @Autowired private val domains: TenantDomainRepository,
    @Autowired private val cacheManager: CacheManager,
) {
    @Configuration
    @EnableCaching
    class Config {
        @Bean
        fun cacheManager(): CacheManager = ConcurrentMapCacheManager("tenantByHost")

        @Bean
        fun tenantDomainRepository(): TenantDomainRepository = mockk()

        @Bean
        fun tenantRepository(): TenantRepository = mockk()

        @Bean
        fun tenantResolver(
            domains: TenantDomainRepository,
            tenants: TenantRepository,
            @Value("\${app.base-domain}") baseDomain: String,
        ) = TenantResolver(domains, tenants, baseDomain)
    }

    private val acme = Tenant(
        id = UUID.randomUUID(),
        companyName = "Acme Tours",
        slug = "acme",
        schemaName = "tenant_acme_0123456789ab",
        status = TenantStatus.ACTIVE,
    )

    @BeforeEach
    fun setUp() {
        cacheManager.getCache("tenantByHost")!!.clear()   // context (and cache) is shared between tests
        clearMocks(tenants, domains)
        every { domains.findActiveByDomain(any()) } returns null
        every { tenants.findBySlug(any()) } returns null
        every { tenants.findBySlug("acme") } returns acme
    }

    @Test
    fun `a resolved tenant is cached`() {
        val first = resolver.resolveByHost("acme.app.example.com")
        val second = resolver.resolveByHost("acme.app.example.com")

        assertEquals(TenantInfo(acme.id, acme.schemaName), first)
        assertEquals(first, second)
        verify(exactly = 1) { tenants.findBySlug("acme") }   // second call came from the cache
    }

    @Test
    fun `a miss is not cached, so a tenant created later is found`() {
        val created =
            Tenant(UUID.randomUUID(), "New Shop", "newshop", "tenant_newshop_ba9876543210", TenantStatus.ACTIVE)
        every { tenants.findBySlug("newshop") } returnsMany listOf(null, created)

        assertNull(resolver.resolveByHost("newshop.app.example.com"))   // tenant does not exist yet
        val afterCreation = resolver.resolveByHost("newshop.app.example.com")

        assertEquals(TenantInfo(created.id, created.schemaName), afterCreation)
        verify(exactly = 2) { tenants.findBySlug("newshop") }
    }

    /*
     * Documents a weakness and FAILS against the current annotation: the cache key is the raw host string,
     * so every spelling of the same host (case, port) gets its own entry. A client can fill the cache with
     * variants of a valid host. Fix: normalize the key (see the answer).
     */
    @Test
    fun `spellings of the same host share one cache entry`() {
        resolver.resolveByHost("acme.app.example.com")
        resolver.resolveByHost("ACME.app.example.com")
        resolver.resolveByHost("acme.app.example.com:8443")

        verify(exactly = 1) { tenants.findBySlug("acme") }
    }
}
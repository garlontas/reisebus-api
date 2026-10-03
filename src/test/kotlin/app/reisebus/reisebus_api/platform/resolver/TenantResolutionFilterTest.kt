package app.reisebus.reisebus_api.platform.resolver

import app.reisebus.reisebus_api.common.context.TenantContext
import app.reisebus.reisebus_api.common.context.TenantInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The filter only sets the context from the host and always clears it afterwards.
 * It must never reject a request itself: authorization is decided later by the security rules.
 * MockMvc runs on the test thread, so the ThreadLocal is directly observable here.
 */
class TenantResolutionFilterTest {

    private val resolver = mockk<TenantResolver>()
    private val filter = TenantResolutionFilter(resolver)
    private val acme = TenantInfo(UUID.randomUUID(), "tenant_acme_0123456789ab")

    @AfterEach
    fun tearDown() = TenantContext.clear()   // static ThreadLocal: must not leak between tests

    @Test
    fun `tenant context is set while the chain runs and cleared afterwards`() {
        every { resolver.resolveByHost("acme.app.example.com") } returns acme
        var seenInChain: TenantInfo? = null

        filter.doFilter(
            request("acme.app.example.com"),
            MockHttpServletResponse(),
        ) { _, _ -> seenInChain = TenantContext.get() }

        assertEquals(acme, seenInChain)
        assertNull(TenantContext.get())
    }

    @Test
    fun `request without a tenant continues with an empty context`() {
        every { resolver.resolveByHost("app.example.com") } returns null   // root domain: signup, /me
        var chainCalled = false
        var seenInChain: TenantInfo? = acme

        filter.doFilter(
            request("app.example.com"),
            MockHttpServletResponse(),
        ) { _, _ ->
            chainCalled = true
            seenInChain = TenantContext.get()
        }

        assertTrue(chainCalled)          // the filter never rejects, it only sets the context
        assertNull(seenInChain)
        assertNull(TenantContext.get())
    }

    @Test
    fun `context is cleared when the chain throws`() {
        every { resolver.resolveByHost(any()) } returns acme

        assertThrows<IllegalStateException> {
            filter.doFilter(
                request("acme.app.example.com"),
                MockHttpServletResponse(),
            ) { _, _ -> throw IllegalStateException("boom") }
        }

        assertNull(TenantContext.get())   // pooled threads are reused: a leak would hit the next request
    }

    @Test
    fun `resolver failure propagates, skips the chain and leaves no context`() {
        every { resolver.resolveByHost(any()) } throws IllegalStateException("database down")
        var chainCalled = false

        assertThrows<IllegalStateException> {
            filter.doFilter(
                request("acme.app.example.com"),
                MockHttpServletResponse(),
            ) { _, _ -> chainCalled = true }
        }

        assertTrue(!chainCalled)
        assertNull(TenantContext.get())
    }

    @Test
    fun `a tenant request does not leak its context into the next request on the same thread`() {
        every { resolver.resolveByHost("acme.app.example.com") } returns acme
        every { resolver.resolveByHost("app.example.com") } returns null
        var seenInSecond: TenantInfo? = acme

        filter.doFilter(request("acme.app.example.com"), MockHttpServletResponse()) { _, _ -> }
        filter.doFilter(
            request("app.example.com"),
            MockHttpServletResponse(),
        ) { _, _ -> seenInSecond = TenantContext.get() }

        assertNull(seenInSecond)
    }

    @Test
    fun `resolver receives the server name without port`() {
        every { resolver.resolveByHost(any()) } returns null
        val request = request("acme.app.example.com").apply { serverPort = 8443 }

        filter.doFilter(request, MockHttpServletResponse()) { _, _ -> }

        verify(exactly = 1) { resolver.resolveByHost("acme.app.example.com") }
    }

    @Test
    fun `chain receives the original request and response`() {
        every { resolver.resolveByHost(any()) } returns null
        val request = request("app.example.com")
        val response = MockHttpServletResponse()
        var chainRequest: ServletRequest? = null
        var chainResponse: ServletResponse? = null

        filter.doFilter(request, response) { req, res ->
            chainRequest = req
            chainResponse = res
        }

        assertSame(request, chainRequest)
        assertSame(response, chainResponse)
    }

    private fun request(host: String) = MockHttpServletRequest().apply { serverName = host }
}

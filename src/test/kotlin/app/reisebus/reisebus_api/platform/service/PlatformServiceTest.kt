package app.reisebus.reisebus_api.platform.service

import app.reisebus.reisebus_api.config.RESERVED_SLUGS
import app.reisebus.reisebus_api.platform.api.contract.SignupProvisioningStatus
import app.reisebus.reisebus_api.platform.api.contract.TenantCreationStatus
import app.reisebus.reisebus_api.platform.domain.Tenant
import app.reisebus.reisebus_api.platform.domain.TenantStatus
import app.reisebus.reisebus_api.platform.messaging.CreateTenant
import app.reisebus.reisebus_api.platform.persistence.TenantRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.springframework.context.ApplicationEventPublisher
import java.util.*
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlatformServiceTest {
    private val repository = mock(TenantRepository::class.java)
    private val eventPublisher = mock(ApplicationEventPublisher::class.java)
    private val service = PlatformService(repository, eventPublisher)

    @Test
    fun `creates tenant and publishes event`() {
        `when`(repository.findBySlug("example-bus")).thenReturn(null)
        `when`(repository.save(any(Tenant::class.java))).thenAnswer { it.arguments[0] }

        val event = service.createTenant(CreateTenant(" Reisebus GmbH ", "Example-Bus"))

        assertEquals("example-bus", event.shopSlug)
        assertTrue(event.schemaName.startsWith("tenant_example_bus_"))
        val tenant = ArgumentCaptor.forClass(Tenant::class.java)
        verify(repository).save(tenant.capture())
        assertEquals(TenantStatus.PROVISIONING, tenant.value.status)
        verify(eventPublisher).publishEvent(event)
    }

    @Test
    fun `rejects duplicate slug`() {
        `when`(repository.findBySlug("example-bus")).thenReturn(
            Tenant(
                UUID.randomUUID(),
                "Reisebus GmbH",
                "example-bus",
                "tenant_example_bus",
                TenantStatus.ACTIVE,
            )
        )

        assertFailsWith<DuplicateShopSlugException> {
            service.createTenant(CreateTenant("Reisebus GmbH", "Example-Bus"))
        }
    }

    @Test
    fun `rejects blank company name`() {
        assertFailsWith<IllegalArgumentException> {
            service.createTenant(CreateTenant("", "Example-Bus"))
        }
    }

    @Test
    fun `rejects blank slug`() {
        assertFailsWith<IllegalArgumentException> {
            service.createTenant(CreateTenant("Reisebus GmbH", ""))
        }
    }

    @Test
    fun `rejects slug not matching regex`() {
        assertFailsWith<IllegalArgumentException> {
            service.createTenant(CreateTenant("Reisebus GmbH", "Example-Bus!"))
        }
    }

    @Test
    fun `rejects slug with less than 3 characters`() {
        assertFailsWith<IllegalArgumentException> {
            service.createTenant(CreateTenant("Reisebus GmbH", "ex"))
        }
    }

    @Test
    fun `rejects slug with more than 40 characters`() {
        assertFailsWith<IllegalArgumentException> {
            service.createTenant(CreateTenant("Reisebus GmbH", "e".repeat(41)))
        }
    }

    @ParameterizedTest
    @MethodSource("reservedSlugs")
    fun `rejects reserved slug`(slug: String) {
        assertFailsWith<IllegalArgumentException> {
            service.createTenant(CreateTenant("Reisebus GmbH", slug))
        }
    }

    companion object {
        @JvmStatic
        fun reservedSlugs(): Stream<String> = RESERVED_SLUGS.stream()
    }

    @Test
    fun `finds tenant by normalized slug`() {
        val tenant = mock(Tenant::class.java)
        `when`(repository.findBySlug("example-bus")).thenReturn(tenant)

        assertNotNull(service.findBySlug(" Example-Bus "))
    }

    @Test
    fun `finds tenant by normalized slug with leading and trailing whitespace`() {
        val tenant = mock(Tenant::class.java)
        `when`(repository.findBySlug("example-bus")).thenReturn(tenant)

        assertNotNull(service.findBySlug(" Example-Bus "))
    }

    @Test
    fun `gets signup provisioning status for tenant`() {
        val id = UUID.randomUUID()
        `when`(repository.findById(id)).thenReturn(
            Optional.of(Tenant(
                id,
                "Reisebus GmbH",
                "example-bus",
                "tenant_example_bus",
                TenantStatus.PROVISIONING,
            ))
        )

        val status = service.getSignupProvisioningStatus(id)

        assertEquals(
            SignupProvisioningStatus(
                id,
                TenantCreationStatus.PROVISIONING,
                shopSlug = "example-bus",
                error = null
            ),
            status
        )
        verify(repository).findById(id)
    }

    @Test
    fun `get signup provisioning status for non existing tenant`() {
        val id = UUID.randomUUID()
        `when`(repository.findById(id)).thenReturn(Optional.empty())

        assertFailsWith<TenantNotFoundException> {
            service.getSignupProvisioningStatus(id)
        }
    }

    @Test
    fun `returns true if slug is available`() {
        val slug = "Example-Bus"
        `when`(repository.findBySlug("example-bus")).thenReturn(null)

        assertTrue(service.isDesiredSlugAvailable(slug))
    }

    @Test
    fun `returns false if slug is not available`() {
        val slug = "Example-Bus"
        `when`(repository.findBySlug("example-bus")).thenReturn(mock(Tenant::class.java))

        assertFalse(service.isDesiredSlugAvailable(slug))
    }

    @Test
    fun `throws IllegalArgumentException if slug is invalid`() {
        val slug = "invalid !slug"
        `when`(repository.findBySlug(slug)).thenReturn(null)

        assertFailsWith<IllegalArgumentException> {
            service.isDesiredSlugAvailable(slug)
        }
    }
}

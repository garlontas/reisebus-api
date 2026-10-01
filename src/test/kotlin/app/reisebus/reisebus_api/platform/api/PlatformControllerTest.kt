package app.reisebus.reisebus_api.platform.api

import app.reisebus.reisebus_api.config.WebConfiguration
import app.reisebus.reisebus_api.platform.api.contract.SignupProvisioningStatus
import app.reisebus.reisebus_api.platform.api.contract.TenantCreationStatus
import app.reisebus.reisebus_api.platform.domain.TenantStatus
import app.reisebus.reisebus_api.platform.messaging.CreateTenant
import app.reisebus.reisebus_api.platform.messaging.TenantCreated
import app.reisebus.reisebus_api.platform.service.DuplicateShopSlugException
import app.reisebus.reisebus_api.platform.service.PlatformService
import app.reisebus.reisebus_api.platform.service.TenantNotFoundException
import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.*
import kotlin.test.assertEquals

@WebMvcTest(PlatformController::class)
@Import(WebConfiguration::class)
class PlatformControllerTest(
    @Autowired private val mvc: MockMvc,
) {
    @MockkBean
    private lateinit var platformService: PlatformService

    @AfterEach
    fun tearDown() = clearMocks(platformService)

    @Test
    fun `POST tenants returns 201 with location and forwards request data to the service`() {
        val tenantId = UUID.randomUUID()
        val command = slot<CreateTenant>()
        every { platformService.createTenant(capture(command)) } returns tenantCreated(
            tenantId,
            TenantStatus.PROVISIONING
        )

        mvc.post(TENANTS_URL) {
            contentType = MediaType.APPLICATION_JSON
            content = createTenantJson()
        }.andExpect {
            status { isCreated() }
            header { string("Location", "/api/platform/signups/$tenantId") }
            jsonPath("$.status") { value("PROVISIONING") }
            jsonPath("$.id") { value(tenantId.toString()) }
        }

        assertEquals(" Reisebus GmbH ", command.captured.companyName)
        assertEquals("Example-Bus", command.captured.shopSlug)
    }

    @ParameterizedTest
    @EnumSource(TenantStatus::class)
    fun `POST tenants returns the status reported by the service`(status: TenantStatus) {
        every { platformService.createTenant(any()) } returns tenantCreated(UUID.randomUUID(), status)

        mvc.post(TENANTS_URL) {
            contentType = MediaType.APPLICATION_JSON
            content = createTenantJson()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.status") { value(status.name) }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "not-an-email", "missing-at.example.com"])
    fun `POST tenants with invalid email returns 400 and does not call the service`(email: String) {
        mvc.post(TENANTS_URL) {
            contentType = MediaType.APPLICATION_JSON
            content = createTenantJson(adminEmail = email)
        }.andExpect {
            status { isBadRequest() }
        }

        verify(exactly = 0) { platformService.createTenant(any()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `POST tenants with blank company name returns 400`(companyName: String) {
        mvc.post(TENANTS_URL) {
            contentType = MediaType.APPLICATION_JSON
            content = createTenantJson(companyName = companyName)
        }.andExpect {
            status { isBadRequest() }
        }

        verify(exactly = 0) { platformService.createTenant(any()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `POST tenants with blank shop slug returns 400`(shopSlug: String) {
        mvc.post(TENANTS_URL) {
            contentType = MediaType.APPLICATION_JSON
            content = createTenantJson(desiredSlug = shopSlug)
        }.andExpect {
            status { isBadRequest() }
        }

        verify(exactly = 0) { platformService.createTenant(any()) }
    }

    @Test
    fun `POST tenants with malformed JSON returns 400`() {
        mvc.post(TENANTS_URL) {
            contentType = MediaType.APPLICATION_JSON
            content = "{ not json"
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST tenants with duplicate slug returns 409`() {
        every { platformService.createTenant(any()) } throws DuplicateShopSlugException("example-bus")
        mvc.post(TENANTS_URL) {
            contentType = MediaType.APPLICATION_JSON
            content = createTenantJson(desiredSlug = "example-bus")
        }.andExpect { status { isConflict() } }
    }

    @ParameterizedTest
    @EnumSource(TenantCreationStatus::class)
    fun `GET provisioning status returns the status reported by the service`(status: TenantCreationStatus) {
        val tenantId = UUID.randomUUID()
        every { platformService.getSignupProvisioningStatus(tenantId) } returns
                SignupProvisioningStatus(
                    id = tenantId,
                    status = status,
                    shopSlug = "test-slug",
                    error = null,
                )

        mvc.get(SIGNUP_STATUS_URL, tenantId).andExpect {
            status { isOk() }
            jsonPath("$.id") { value(tenantId.toString()) }
            jsonPath("$.status") { value(status.name) }
            jsonPath("$.shopSlug") { value("test-slug") }
        }

        verify(exactly = 1) { platformService.getSignupProvisioningStatus(tenantId) }
    }

    @Test
    fun `GET provisioning status exposes the error when provisioning failed`() {
        val tenantId = UUID.randomUUID()
        every { platformService.getSignupProvisioningStatus(tenantId) } returns
                SignupProvisioningStatus(
                    id = tenantId,
                    status = TenantCreationStatus.FAILED,
                    shopSlug = "failed-slug",
                    error = "Provisioning failed",
                )

        mvc.get(SIGNUP_STATUS_URL, tenantId).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("FAILED") }
            jsonPath("$.error") { value("Provisioning failed") }
        }
    }

    @Test
    fun `GET provisioning status with malformed id returns 400`() {
        mvc.get(SIGNUP_STATUS_URL, "not-a-uuid").andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `GET provisioning status for unknown tenant returns 404`() {
        val tenantId = UUID.randomUUID()
        every { platformService.getSignupProvisioningStatus(tenantId) } throws TenantNotFoundException(tenantId)

        mvc.get(SIGNUP_STATUS_URL, tenantId).andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `GET slug availability for unused slug returns true`() {
        val slug = "example-bus"
        every { platformService.isDesiredSlugAvailable(slug) } returns true

        mvc.get(SLUG_AVAILABILITY_URL, slug).andExpect {
            status { isOk() }
            jsonPath("$.slug") { value(slug) }
            jsonPath("$.available") { value(true) }
        }
    }

    @Test
    fun `GET slug availability for used slug returns false`() {
        val slug = "example-bus"
        every { platformService.isDesiredSlugAvailable(slug) } returns false

        mvc.get(SLUG_AVAILABILITY_URL, slug).andExpect {
            status { isOk() }
            jsonPath("$.slug") { value(slug) }
            jsonPath("$.available") { value(false) }
        }
    }

    @Test
    fun `GET slug availability for invalid slug throws exception`() {
        val slug = "invalid-slug"
        every { platformService.isDesiredSlugAvailable(slug) } throws IllegalArgumentException("Shop slug must contain only lowercase letters, numbers, and hyphens")

        mvc.get(SLUG_AVAILABILITY_URL, slug).andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `GET slug availability for empty slug throws exception`() {
        val slug = ""
        every { platformService.isDesiredSlugAvailable(slug) } throws IllegalArgumentException("Shop slug must contain only lowercase letters, numbers, and hyphens")

        mvc.get(SLUG_AVAILABILITY_URL, slug).andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `GET slug availability normalized slug returns availability`() {
        val slug = "Example-Bus"
        every { platformService.isDesiredSlugAvailable(slug) } returns true

        mvc.get(SLUG_AVAILABILITY_URL, slug).andExpect {
            status { isOk() }
            jsonPath("$.slug") { value("example-bus") }
            jsonPath("$.available") { value(true) }
        }
    }

    private fun tenantCreated(id: UUID, status: TenantStatus) = TenantCreated(
        tenantId = id,
        companyName = "Reisebus GmbH",
        shopSlug = "example-bus",
        schemaName = "tenant_example_bus_${id.toString().takeLast(12)}",
        status = status,
    )

    private fun createTenantJson(
        companyName: String = " Reisebus GmbH ",
        desiredSlug: String = "Example-Bus",
        adminEmail: String = "admin@example.com",
    ) = """{"companyName":"$companyName","desiredSlug":"$desiredSlug","adminEmail":"$adminEmail"}"""

    private companion object {
        const val TENANTS_URL = "/api/platform/signups"
        const val SIGNUP_STATUS_URL = "/api/platform/signups/{id}"
        const val SLUG_AVAILABILITY_URL = "/api/platform/slug-availability?slug={slug}"
    }
}

package app.reisebus.reisebus_api.platform.api

import app.reisebus.reisebus_api.config.TestSecurityConfiguration
import app.reisebus.reisebus_api.platform.domain.TenantStatus
import app.reisebus.reisebus_api.platform.messaging.CreateTenant
import app.reisebus.reisebus_api.platform.messaging.TenantCreated
import app.reisebus.reisebus_api.platform.resolver.TenantResolutionFilter
import app.reisebus.reisebus_api.platform.security.AppJwtConverter
import app.reisebus.reisebus_api.platform.service.DuplicateShopSlugException
import app.reisebus.reisebus_api.platform.service.PlatformService
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
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.*
import kotlin.test.assertEquals

@WebMvcTest(
    controllers = [PlatformController::class],
    excludeFilters = [ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = [TenantResolutionFilter::class, AppJwtConverter::class],
    )],
)
@Import(TestSecurityConfiguration::class)
class PlatformControllerTest(
    @Autowired private val mvc: MockMvc
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
            with(jwt())
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
            with(jwt())
            contentType = MediaType.APPLICATION_JSON
            content = createTenantJson()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.status") { value(status.name) }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `POST tenants with blank company name returns 400`(companyName: String) {
        mvc.post(TENANTS_URL) {
            with(jwt())
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
            with(jwt())
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
            with(jwt())
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
            with(jwt())
            contentType = MediaType.APPLICATION_JSON
            content = createTenantJson(desiredSlug = "example-bus")
        }.andExpect { status { isConflict() } }
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
    ) = """{"companyName":"$companyName","desiredSlug":"$desiredSlug"}"""

    private companion object {
        const val TENANTS_URL = "/api/platform/signups"
    }
}

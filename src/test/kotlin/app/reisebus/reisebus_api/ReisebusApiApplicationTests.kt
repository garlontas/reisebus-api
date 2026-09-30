package app.reisebus.reisebus_api

import app.reisebus.reisebus_api.platform.TestcontainersConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import kotlin.test.Test

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class ReisebusApiApplicationTests {
	@Test
	fun contextLoads() {}
}

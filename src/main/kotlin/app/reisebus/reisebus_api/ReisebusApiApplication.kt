package app.reisebus.reisebus_api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import java.time.Clock

@SpringBootApplication
class ReisebusApiApplication {
	@Bean
	fun clock(): Clock = Clock.systemUTC()
}

fun main(args: Array<String>) {
	runApplication<ReisebusApiApplication>(*args)
}

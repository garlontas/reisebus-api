package app.reisebus.reisebus_api.platform.service

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

// TODO: Implement with your mail sender.
/**
 * Deliberately NOT an event: Spring Modulith persists serialized events in event_publication,
 * which would store the raw invitation token in the database. Implement with your mail sender.
 */
interface InvitationMailer {
    fun send(email: String, companyName: String, role: String, acceptUrl: String)
}

@Component
@Profile("!prod")
class LoggingInvitationMailer : InvitationMailer {
    private val log = LoggerFactory.getLogger(javaClass)
    override fun send(email: String, companyName: String, role: String, acceptUrl: String) {
        log.info("DEV invitation for {} ({} @ {}): {}", email, role, companyName, acceptUrl)
    }
}

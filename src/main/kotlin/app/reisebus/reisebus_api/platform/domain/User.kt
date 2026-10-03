package app.reisebus.reisebus_api.platform.domain

import app.reisebus.reisebus_api.common.persistence.AuditedEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.*

@Entity
@Table(name = "users", schema = "platform")
class User(
    @Id
    var id: UUID,

    @Column(nullable = false, unique = true, length = 255)
    var email: String,

    @Column(name = "identity_subject", nullable = false, unique = true, length = 255)
    var identitySubject: String,

    @Size(max = 255)
    @NotNull
    @Column(name = "identity_issuer", nullable = false)
    var identityIssuer: String = "",

    @Column(name = "first_name", nullable = false, length = 100)
    var firstName: String,

    @Column(name = "last_name", nullable = false, length = 100)
    var lastName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: UserStatus,
) : AuditedEntity()

enum class UserStatus {
    INVITED,
    ACTIVE,
    DISABLED
}



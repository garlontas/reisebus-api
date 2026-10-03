package app.reisebus.reisebus_api.platform.domain

import app.reisebus.reisebus_api.common.persistence.AuditedEntity
import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "platform_users", schema = "platform")
class PlatformUser(
    @Id
    @Column(name = "user_id")
    var userId: UUID,

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    var user: User,
) : AuditedEntity()



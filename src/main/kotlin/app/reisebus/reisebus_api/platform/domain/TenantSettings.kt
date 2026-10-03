package app.reisebus.reisebus_api.platform.domain

import app.reisebus.reisebus_api.common.persistence.AuditedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import org.hibernate.annotations.ColumnDefault
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.*

@Entity
@Table(name = "tenant_settings", schema = "platform")
class TenantSettings(
    @Id
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @Column(name = "contact_email", nullable = false)
    var contactEmail: String? = null,

    @NotNull
    @ColumnDefault("'{}'")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "branding", nullable = false)
    var branding: Map<String, Any>? = null
) : AuditedEntity()

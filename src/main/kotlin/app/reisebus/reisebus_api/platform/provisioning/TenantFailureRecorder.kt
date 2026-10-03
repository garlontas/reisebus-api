package app.reisebus.reisebus_api.platform.provisioning

import app.reisebus.reisebus_api.platform.domain.TenantStatus
import app.reisebus.reisebus_api.platform.persistence.TenantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
internal class TenantFailureRecorder(private val tenantRepository: TenantRepository) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markFailed(tenantId: UUID) {
        tenantRepository.findById(tenantId).ifPresent { it.status = TenantStatus.FAILED }
    }
}

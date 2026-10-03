package app.reisebus.reisebus_api.common.context

import java.util.*

data class TenantInfo(val id: UUID, val schemaName: String)

object TenantContext {
    private val current = ThreadLocal<TenantInfo?>()
    fun set(t: TenantInfo) = current.set(t)
    fun get(): TenantInfo? = current.get()
    fun clear() = current.remove()
}

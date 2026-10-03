package app.reisebus.reisebus_api.common.context

/** DB values match ck_user_tenants_role. The JWT converter turns them into ROLE_TENANT_ADMIN etc. */
enum class TenantRole(val value: String) {
    TENANT_ADMIN("tenant_admin"), STAFF("staff"), DRIVER("driver")
}

/** Matches ck_invitation_role: nobody can be invited as tenant_admin. */
enum class InvitableRole(val role: TenantRole) { STAFF(TenantRole.STAFF), DRIVER(TenantRole.DRIVER) }

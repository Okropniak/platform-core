alter table entitlement.organization_entitlements
drop constraint organization_entitlements_source_check;

alter table entitlement.organization_entitlements
add constraint organization_entitlements_source_check
    check (source in ('plan', 'manual', 'promo', 'enterprise', 'admin_override'));

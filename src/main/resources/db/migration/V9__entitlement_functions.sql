create or replace function entitlement.get_entitlements(
    p_organization_id uuid,
    p_product_code text
)
returns jsonb
language sql
stable
security definer
set search_path = entitlement, platform, pg_temp
as $$
    select coalesce(
        jsonb_object_agg(
            oe.feature_code,
            jsonb_build_object(
                'enabled', oe.enabled,
                'metric_code', oe.metric_code,
                'limit_value', oe.limit_value,
                'period', oe.period,
                'source', oe.source
            )
            order by oe.feature_code
        ),
        '{}'::jsonb
    )
    from entitlement.organization_entitlements oe
    where oe.organization_id = p_organization_id
      and oe.product_code = p_product_code
      and oe.enabled = true
      and oe.valid_from <= now()
      and (oe.valid_until is null or oe.valid_until > now());
$$;

revoke all on function entitlement.get_entitlements(uuid, text) from public;
grant execute on function entitlement.get_entitlements(uuid, text) to platform_backend_role;

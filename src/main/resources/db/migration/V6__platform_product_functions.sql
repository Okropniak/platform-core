create or replace function platform.has_product_access(
    p_organization_id uuid,
    p_user_id uuid,
    p_product_code text
)
returns boolean
language sql
stable
security definer
set search_path = platform, pg_temp
as $$
    select exists (
        select 1
        from platform.product_access pa
        join platform.products p on p.code = pa.product_code
        where pa.organization_id = p_organization_id
          and pa.user_id = p_user_id
          and pa.product_code = p_product_code
          and pa.enabled
          and p.status = 'active'
    );
$$;

revoke all on function platform.has_product_access(uuid, uuid, text) from public;
grant execute on function platform.has_product_access(uuid, uuid, text) to platform_backend_role;
grant execute on function platform.has_product_access(uuid, uuid, text) to authenticated;

create or replace function platform.check_product_access(
    p_organization_id uuid,
    p_user_id uuid,
    p_product_code text
)
returns jsonb
language plpgsql
stable
security definer
set search_path = platform, pg_temp
as $$
declare
    v_allowed boolean := false;
    v_role text;
begin
    select pa.enabled, pa.role
    into v_allowed, v_role
    from platform.product_access pa
    join platform.products p on p.code = pa.product_code
    where pa.organization_id = p_organization_id
      and pa.user_id = p_user_id
      and pa.product_code = p_product_code
      and p.status = 'active';

    return jsonb_build_object(
        'allowed', coalesce(v_allowed, false),
        'role', v_role
    );
end;
$$;

revoke all on function platform.check_product_access(uuid, uuid, text) from public;
grant execute on function platform.check_product_access(uuid, uuid, text) to platform_backend_role;

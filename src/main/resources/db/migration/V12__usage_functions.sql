create or replace function usage.current_period_bounds(p_period text)
returns table(period_start timestamptz, period_end timestamptz)
language sql
stable
security definer
set search_path = usage, pg_temp
as $$
    select case p_period
               when 'daily' then date_trunc('day', now())
               when 'yearly' then date_trunc('year', now())
               when 'lifetime' then '1970-01-01 00:00:00+00'::timestamptz
               else date_trunc('month', now())
           end,
           case p_period
               when 'daily' then date_trunc('day', now()) + interval '1 day'
               when 'yearly' then date_trunc('year', now()) + interval '1 year'
               when 'lifetime' then '9999-12-31 00:00:00+00'::timestamptz
               else date_trunc('month', now()) + interval '1 month'
           end;
$$;

revoke all on function usage.current_period_bounds(text) from public;
grant execute on function usage.current_period_bounds(text) to platform_backend_role;

create or replace function usage.consume_usage(
    p_organization_id uuid,
    p_user_id uuid,
    p_product_code text,
    p_metric_code text,
    p_amount numeric,
    p_idempotency_key text
)
returns jsonb
language plpgsql
security definer
set search_path = usage, entitlement, platform, pg_temp
as $$
declare
    v_event_id bigint;
    v_existing jsonb;
    v_limit numeric;
    v_period text;
    v_period_start timestamptz;
    v_period_end timestamptz;
    v_counter usage.usage_counters%rowtype;
    v_result jsonb;
begin
    if p_amount <= 0 then
        return jsonb_build_object('accepted', false, 'reason', 'invalid_amount');
    end if;

    insert into usage.usage_events (
        organization_id, user_id, product_code, metric_code,
        amount, event_type, counter_scope, idempotency_key, metadata
    )
    values (
        p_organization_id, p_user_id, p_product_code, p_metric_code,
        p_amount, 'consume', 'organization', p_idempotency_key,
        jsonb_build_object('status', 'processing')
    )
    on conflict (organization_id, user_id, product_code, metric_code, event_type, idempotency_key)
    where idempotency_key is not null
    do nothing
    returning id into v_event_id;

    if v_event_id is null then
        select metadata->'result'
        into v_existing
        from usage.usage_events
        where organization_id = p_organization_id
          and user_id is not distinct from p_user_id
          and product_code = p_product_code
          and metric_code = p_metric_code
          and event_type = 'consume'
          and idempotency_key = p_idempotency_key;

        return coalesce(v_existing, jsonb_build_object('accepted', false, 'reason', 'idempotency_result_unavailable'));
    end if;

    select oe.limit_value, oe.period
    into v_limit, v_period
    from entitlement.organization_entitlements oe
    where oe.organization_id = p_organization_id
      and oe.product_code = p_product_code
      and oe.metric_code = p_metric_code
      and oe.enabled = true
      and oe.valid_from <= now()
      and (oe.valid_until is null or oe.valid_until > now())
    order by oe.valid_from desc
    limit 1;

    if not found then
        v_result := jsonb_build_object('accepted', false, 'reason', 'no_entitlement');
        update usage.usage_events set metadata = jsonb_build_object('result', v_result) where id = v_event_id;
        return v_result;
    end if;

    select period_start, period_end
    into v_period_start, v_period_end
    from usage.current_period_bounds(coalesce(v_period, 'monthly'));

    insert into usage.usage_counters (
        organization_id, product_code, metric_code,
        period_start, period_end, used_value, reserved_value, counter_scope
    )
    values (
        p_organization_id, p_product_code, p_metric_code,
        v_period_start, v_period_end, 0, 0, 'organization'
    )
    on conflict (organization_id, product_code, metric_code, period_start, period_end)
    where counter_scope = 'organization'
    do nothing;

    update usage.usage_counters
    set used_value = used_value + p_amount,
        updated_at = now()
    where organization_id = p_organization_id
      and product_code = p_product_code
      and metric_code = p_metric_code
      and period_start = v_period_start
      and period_end = v_period_end
      and counter_scope = 'organization'
      and (v_limit is null or used_value + reserved_value + p_amount <= v_limit)
    returning * into v_counter;

    if not found then
        select *
        into v_counter
        from usage.usage_counters
        where organization_id = p_organization_id
          and product_code = p_product_code
          and metric_code = p_metric_code
          and period_start = v_period_start
          and period_end = v_period_end
          and counter_scope = 'organization';

        v_result := jsonb_build_object(
            'accepted', false,
            'reason', 'limit_exceeded',
            'limit', v_limit,
            'used', v_counter.used_value,
            'reserved', v_counter.reserved_value,
            'remaining', greatest(v_limit - v_counter.used_value - v_counter.reserved_value, 0)
        );
        update usage.usage_events set metadata = jsonb_build_object('result', v_result) where id = v_event_id;
        return v_result;
    end if;

    v_result := jsonb_build_object(
        'accepted', true,
        'used', v_counter.used_value,
        'reserved', v_counter.reserved_value,
        'limit', v_limit,
        'remaining', case when v_limit is null then null else greatest(v_limit - v_counter.used_value - v_counter.reserved_value, 0) end
    );
    update usage.usage_events set metadata = jsonb_build_object('result', v_result) where id = v_event_id;
    return v_result;
end;
$$;

create or replace function usage.reserve_usage(
    p_organization_id uuid,
    p_user_id uuid,
    p_product_code text,
    p_metric_code text,
    p_amount numeric,
    p_idempotency_key text
)
returns jsonb
language plpgsql
security definer
set search_path = usage, entitlement, platform, pg_temp
as $$
declare
    v_reservation_id uuid;
    v_existing jsonb;
    v_limit numeric;
    v_period text;
    v_period_start timestamptz;
    v_period_end timestamptz;
    v_counter usage.usage_counters%rowtype;
    v_result jsonb;
begin
    if p_amount <= 0 then
        return jsonb_build_object('accepted', false, 'reason', 'invalid_amount');
    end if;

    insert into usage.usage_reservations (
        organization_id, user_id, product_code, metric_code,
        reserved_amount, counter_scope, idempotency_key, metadata
    )
    values (
        p_organization_id, p_user_id, p_product_code, p_metric_code,
        p_amount, 'organization_and_user', p_idempotency_key,
        jsonb_build_object('status', 'processing')
    )
    on conflict (organization_id, user_id, product_code, metric_code, idempotency_key)
    do nothing
    returning id into v_reservation_id;

    if v_reservation_id is null then
        select metadata->'result'
        into v_existing
        from usage.usage_reservations
        where organization_id = p_organization_id
          and user_id is not distinct from p_user_id
          and product_code = p_product_code
          and metric_code = p_metric_code
          and idempotency_key = p_idempotency_key;

        return coalesce(v_existing, jsonb_build_object('accepted', false, 'reason', 'idempotency_result_unavailable'));
    end if;

    select oe.limit_value, oe.period
    into v_limit, v_period
    from entitlement.organization_entitlements oe
    where oe.organization_id = p_organization_id
      and oe.product_code = p_product_code
      and oe.metric_code = p_metric_code
      and oe.enabled = true
      and oe.valid_from <= now()
      and (oe.valid_until is null or oe.valid_until > now())
    order by oe.valid_from desc
    limit 1;

    if not found then
        v_result := jsonb_build_object('accepted', false, 'reason', 'no_entitlement', 'reservationId', v_reservation_id);
        update usage.usage_reservations set status = 'cancelled', metadata = jsonb_build_object('result', v_result) where id = v_reservation_id;
        return v_result;
    end if;

    select period_start, period_end
    into v_period_start, v_period_end
    from usage.current_period_bounds(coalesce(v_period, 'monthly'));

    insert into usage.usage_counters (
        organization_id, product_code, metric_code,
        period_start, period_end, used_value, reserved_value, counter_scope
    )
    values (
        p_organization_id, p_product_code, p_metric_code,
        v_period_start, v_period_end, 0, 0, 'organization'
    )
    on conflict (organization_id, product_code, metric_code, period_start, period_end)
    where counter_scope = 'organization'
    do nothing;

    update usage.usage_counters
    set reserved_value = reserved_value + p_amount,
        updated_at = now()
    where organization_id = p_organization_id
      and product_code = p_product_code
      and metric_code = p_metric_code
      and period_start = v_period_start
      and period_end = v_period_end
      and counter_scope = 'organization'
      and (v_limit is null or used_value + reserved_value + p_amount <= v_limit)
    returning * into v_counter;

    if not found then
        select *
        into v_counter
        from usage.usage_counters
        where organization_id = p_organization_id
          and product_code = p_product_code
          and metric_code = p_metric_code
          and period_start = v_period_start
          and period_end = v_period_end
          and counter_scope = 'organization';

        v_result := jsonb_build_object(
            'accepted', false,
            'reason', 'limit_exceeded',
            'reservationId', v_reservation_id,
            'limit', v_limit,
            'used', v_counter.used_value,
            'reserved', v_counter.reserved_value,
            'remaining', greatest(v_limit - v_counter.used_value - v_counter.reserved_value, 0)
        );
        update usage.usage_reservations set status = 'cancelled', metadata = jsonb_build_object('result', v_result) where id = v_reservation_id;
        return v_result;
    end if;

    insert into usage.usage_events (
        organization_id, user_id, product_code, metric_code,
        amount, event_type, counter_scope, reservation_id, idempotency_key
    )
    values (
        p_organization_id, p_user_id, p_product_code, p_metric_code,
        p_amount, 'reserve', 'organization', v_reservation_id, p_idempotency_key
    );

    v_result := jsonb_build_object(
        'accepted', true,
        'reservationId', v_reservation_id,
        'reserved', p_amount,
        'used', v_counter.used_value,
        'totalReserved', v_counter.reserved_value,
        'limit', v_limit,
        'remaining', case when v_limit is null then null else greatest(v_limit - v_counter.used_value - v_counter.reserved_value, 0) end
    );
    update usage.usage_reservations set metadata = jsonb_build_object('result', v_result) where id = v_reservation_id;
    return v_result;
end;
$$;

create or replace function usage.finalize_usage(
    p_reservation_id uuid,
    p_user_id uuid,
    p_actual_amount numeric
)
returns jsonb
language plpgsql
security definer
set search_path = usage, entitlement, platform, pg_temp
as $$
declare
    r usage.usage_reservations%rowtype;
    v_limit numeric;
    v_period text;
    v_period_start timestamptz;
    v_period_end timestamptz;
    v_counter usage.usage_counters%rowtype;
    v_release numeric;
    v_result jsonb;
begin
    if p_actual_amount < 0 then
        return jsonb_build_object('finalized', false, 'reason', 'invalid_amount');
    end if;

    select *
    into r
    from usage.usage_reservations
    where id = p_reservation_id
    for update;

    if not found then
        return jsonb_build_object('finalized', false, 'reason', 'reservation_not_found');
    end if;

    if r.user_id is distinct from p_user_id then
        return jsonb_build_object('finalized', false, 'reason', 'reservation_user_mismatch');
    end if;

    if r.status = 'finalized' then
        return coalesce(r.metadata->'finalizeResult', jsonb_build_object('finalized', true, 'idempotent', true));
    end if;

    if r.status <> 'reserved' then
        return jsonb_build_object('finalized', false, 'reason', 'reservation_not_active');
    end if;

    select oe.limit_value, oe.period
    into v_limit, v_period
    from entitlement.organization_entitlements oe
    where oe.organization_id = r.organization_id
      and oe.product_code = r.product_code
      and oe.metric_code = r.metric_code
      and oe.enabled = true
      and oe.valid_from <= now()
      and (oe.valid_until is null or oe.valid_until > now())
    order by oe.valid_from desc
    limit 1;

    v_period_start := case coalesce(v_period, 'monthly')
        when 'daily' then date_trunc('day', r.created_at)
        when 'yearly' then date_trunc('year', r.created_at)
        when 'lifetime' then '1970-01-01 00:00:00+00'::timestamptz
        else date_trunc('month', r.created_at)
    end;
    v_period_end := case coalesce(v_period, 'monthly')
        when 'daily' then date_trunc('day', r.created_at) + interval '1 day'
        when 'yearly' then date_trunc('year', r.created_at) + interval '1 year'
        when 'lifetime' then '9999-12-31 00:00:00+00'::timestamptz
        else date_trunc('month', r.created_at) + interval '1 month'
    end;

    update usage.usage_counters
    set reserved_value = reserved_value - r.reserved_amount,
        used_value = used_value + p_actual_amount,
        updated_at = now()
    where organization_id = r.organization_id
      and product_code = r.product_code
      and metric_code = r.metric_code
      and period_start = v_period_start
      and period_end = v_period_end
      and counter_scope = 'organization'
      and reserved_value >= r.reserved_amount
      and (v_limit is null or used_value + reserved_value - r.reserved_amount + p_actual_amount <= v_limit)
    returning * into v_counter;

    if not found then
        return jsonb_build_object('finalized', false, 'reason', 'limit_exceeded');
    end if;

    v_release := r.reserved_amount - p_actual_amount;

    update usage.usage_reservations
    set status = 'finalized',
        actual_amount = p_actual_amount,
        finalized_at = now()
    where id = p_reservation_id;

    insert into usage.usage_events (
        organization_id, user_id, product_code, metric_code,
        amount, event_type, counter_scope, reservation_id, idempotency_key
    )
    values (
        r.organization_id, r.user_id, r.product_code, r.metric_code,
        p_actual_amount, 'finalize', 'organization', r.id, r.idempotency_key
    );

    v_result := jsonb_build_object(
        'finalized', true,
        'reservationId', r.id,
        'actualAmount', p_actual_amount,
        'releasedAmount', v_release,
        'used', v_counter.used_value,
        'reserved', v_counter.reserved_value
    );

    update usage.usage_reservations
    set metadata = metadata || jsonb_build_object('finalizeResult', v_result)
    where id = p_reservation_id;

    return v_result;
end;
$$;

revoke all on function usage.consume_usage(uuid, uuid, text, text, numeric, text) from public;
revoke all on function usage.reserve_usage(uuid, uuid, text, text, numeric, text) from public;
revoke all on function usage.finalize_usage(uuid, uuid, numeric) from public;
grant execute on function usage.consume_usage(uuid, uuid, text, text, numeric, text) to platform_backend_role;
grant execute on function usage.reserve_usage(uuid, uuid, text, text, numeric, text) to platform_backend_role;
grant execute on function usage.finalize_usage(uuid, uuid, numeric) to platform_backend_role;

create table usage.usage_counters (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references platform.organizations(id) on delete cascade,
    user_id uuid references auth.users(id) on delete cascade,
    product_code text not null references platform.products(code),
    metric_code text not null,
    period_start timestamptz not null,
    period_end timestamptz not null,
    used_value numeric not null default 0,
    reserved_value numeric not null default 0,
    counter_scope text not null default 'organization',
    updated_at timestamptz not null default now(),

    constraint usage_counters_metric_fk
        foreign key (product_code, metric_code)
        references entitlement.metrics(product_code, metric_code),

    constraint usage_counters_scope_check
        check (counter_scope in ('organization', 'user')),

    constraint usage_counters_user_scope_check
        check (
            (counter_scope = 'organization' and user_id is null)
            or
            (counter_scope = 'user' and user_id is not null)
        ),

    constraint usage_counters_values_check
        check (used_value >= 0 and reserved_value >= 0)
);

create unique index usage_counters_org_unique
on usage.usage_counters (
    organization_id,
    product_code,
    metric_code,
    period_start,
    period_end
)
where counter_scope = 'organization';

create unique index usage_counters_user_unique
on usage.usage_counters (
    organization_id,
    user_id,
    product_code,
    metric_code,
    period_start,
    period_end
)
where counter_scope = 'user';

create table usage.usage_reservations (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references platform.organizations(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    product_code text not null references platform.products(code),
    metric_code text not null,
    reserved_amount numeric not null,
    actual_amount numeric,
    counter_scope text not null default 'organization',
    status text not null default 'reserved',
    idempotency_key text not null,
    created_at timestamptz not null default now(),
    finalized_at timestamptz,
    expires_at timestamptz not null default (now() + interval '15 minutes'),
    metadata jsonb not null default '{}'::jsonb,

    constraint usage_reservations_metric_fk
        foreign key (product_code, metric_code)
        references entitlement.metrics(product_code, metric_code),

    constraint usage_reservations_amount_check
        check (reserved_amount >= 0 and (actual_amount is null or actual_amount >= 0)),

    constraint usage_reservations_status_check
        check (status in ('reserved', 'finalized', 'cancelled', 'expired')),

    constraint usage_reservations_scope_check
        check (counter_scope in ('organization', 'user', 'organization_and_user'))
);

create unique index usage_reservations_idempotency_unique
on usage.usage_reservations (
    organization_id,
    user_id,
    product_code,
    metric_code,
    idempotency_key
)
nulls not distinct;

create table usage.usage_events (
    id bigserial primary key,
    organization_id uuid not null references platform.organizations(id) on delete cascade,
    user_id uuid references auth.users(id) on delete set null,
    product_code text not null references platform.products(code),
    metric_code text not null,
    amount numeric not null,
    event_type text not null,
    counter_scope text not null default 'organization',
    reservation_id uuid references usage.usage_reservations(id) on delete set null,
    idempotency_key text,
    created_at timestamptz not null default now(),
    metadata jsonb not null default '{}'::jsonb,

    constraint usage_events_metric_fk
        foreign key (product_code, metric_code)
        references entitlement.metrics(product_code, metric_code),

    constraint usage_events_amount_check
        check (amount >= 0),

    constraint usage_events_event_type_check
        check (event_type in ('consume', 'reserve', 'finalize', 'release', 'cancel')),

    constraint usage_events_scope_check
        check (counter_scope in ('organization', 'user', 'organization_and_user'))
);

create unique index usage_events_idempotency_unique
on usage.usage_events (
    organization_id,
    user_id,
    product_code,
    metric_code,
    event_type,
    idempotency_key
)
nulls not distinct
where idempotency_key is not null;

create index usage_events_org_product_metric_created_idx
on usage.usage_events (
    organization_id,
    product_code,
    metric_code,
    created_at desc
);

create index usage_events_user_product_metric_created_idx
on usage.usage_events (
    user_id,
    product_code,
    metric_code,
    created_at desc
);

create index usage_events_reservation_idx
on usage.usage_events (reservation_id);

grant select, insert, update, delete on usage.usage_counters to platform_backend_role;
grant select, insert, update, delete on usage.usage_reservations to platform_backend_role;
grant select, insert, update, delete on usage.usage_events to platform_backend_role;
grant usage, select on all sequences in schema usage to platform_backend_role;

grant select on usage.usage_counters to authenticated;
grant select on usage.usage_reservations to authenticated;
grant select on usage.usage_events to authenticated;

alter table usage.usage_counters enable row level security;
alter table usage.usage_reservations enable row level security;
alter table usage.usage_events enable row level security;

create policy usage_counters_select_member
on usage.usage_counters
for select
to authenticated
using (platform.is_org_member(organization_id, (select auth.uid())));

create policy usage_reservations_select_member
on usage.usage_reservations
for select
to authenticated
using (platform.is_org_member(organization_id, (select auth.uid())));

create policy usage_events_select_member
on usage.usage_events
for select
to authenticated
using (platform.is_org_member(organization_id, (select auth.uid())));

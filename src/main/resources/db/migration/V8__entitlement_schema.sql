create table entitlement.features (
    id uuid primary key default gen_random_uuid(),
    product_code text not null references platform.products(code),
    feature_code text not null,
    name text not null,
    description text,
    active boolean not null default true,

    unique (product_code, feature_code)
);

create table entitlement.metrics (
    id uuid primary key default gen_random_uuid(),
    product_code text not null references platform.products(code),
    metric_code text not null,
    unit text not null,
    aggregation text not null default 'sum',
    period text not null default 'monthly',

    unique (product_code, metric_code),

    constraint metrics_aggregation_check
        check (aggregation in ('sum', 'max', 'latest')),

    constraint metrics_period_check
        check (period in ('daily', 'monthly', 'yearly', 'lifetime'))
);

create table entitlement.organization_entitlements (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references platform.organizations(id) on delete cascade,
    product_code text not null references platform.products(code),
    feature_code text not null,
    metric_code text,
    enabled boolean not null default true,
    limit_value numeric,
    period text default 'monthly',
    source text not null default 'plan',
    valid_from timestamptz not null default now(),
    valid_until timestamptz,

    unique nulls not distinct (organization_id, product_code, feature_code, metric_code),

    constraint organization_entitlements_feature_fk
        foreign key (product_code, feature_code)
        references entitlement.features(product_code, feature_code),

    constraint organization_entitlements_metric_fk
        foreign key (product_code, metric_code)
        references entitlement.metrics(product_code, metric_code),

    constraint organization_entitlements_source_check
        check (source in ('plan', 'manual', 'promo', 'enterprise')),

    constraint organization_entitlements_period_check
        check (period in ('daily', 'monthly', 'yearly', 'lifetime'))
);

create table entitlement.user_entitlements (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references platform.organizations(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    product_code text not null references platform.products(code),
    feature_code text not null,
    metric_code text,
    enabled boolean not null default true,
    limit_value numeric,
    period text default 'monthly',
    source text not null default 'manual',
    valid_from timestamptz not null default now(),
    valid_until timestamptz,

    unique nulls not distinct (organization_id, user_id, product_code, feature_code, metric_code),

    constraint user_entitlements_feature_fk
        foreign key (product_code, feature_code)
        references entitlement.features(product_code, feature_code),

    constraint user_entitlements_metric_fk
        foreign key (product_code, metric_code)
        references entitlement.metrics(product_code, metric_code),

    constraint user_entitlements_source_check
        check (source in ('manual', 'policy', 'promo', 'enterprise', 'admin_override')),

    constraint user_entitlements_period_check
        check (period in ('daily', 'monthly', 'yearly', 'lifetime'))
);

create index organization_entitlements_active_idx
    on entitlement.organization_entitlements (organization_id, product_code, feature_code, metric_code)
    where enabled and valid_until is null;

create index user_entitlements_active_idx
    on entitlement.user_entitlements (organization_id, user_id, product_code, feature_code, metric_code)
    where enabled and valid_until is null;

grant select, insert, update, delete on entitlement.features to platform_backend_role;
grant select, insert, update, delete on entitlement.metrics to platform_backend_role;
grant select, insert, update, delete on entitlement.organization_entitlements to platform_backend_role;
grant select, insert, update, delete on entitlement.user_entitlements to platform_backend_role;

grant select on entitlement.features to authenticated;
grant select on entitlement.metrics to authenticated;
grant select on entitlement.organization_entitlements to authenticated;
grant select on entitlement.user_entitlements to authenticated;

alter table entitlement.organization_entitlements enable row level security;
alter table entitlement.user_entitlements enable row level security;

create policy organization_entitlements_select_member
on entitlement.organization_entitlements
for select
to authenticated
using (platform.is_org_member(organization_id, (select auth.uid())));

create policy user_entitlements_select_member
on entitlement.user_entitlements
for select
to authenticated
using (platform.is_org_member(organization_id, (select auth.uid())));

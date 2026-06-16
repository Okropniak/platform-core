create table billing.plans (
    id uuid primary key default gen_random_uuid(),
    product_code text not null references platform.products(code),
    plan_code text not null,
    name text not null,
    price numeric(12,2),
    currency text default 'USD',
    billing_period text default 'monthly',
    active boolean not null default true,
    created_at timestamptz not null default now(),

    unique (product_code, plan_code),

    constraint billing_period_check
        check (billing_period in ('monthly', 'yearly', 'one_time', 'manual'))
);

create table billing.subscriptions (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references platform.organizations(id) on delete cascade,
    product_code text not null references platform.products(code),
    plan_code text not null,
    status text not null default 'trial',
    provider text not null default 'manual',
    provider_customer_id text,
    provider_subscription_id text,
    current_period_start timestamptz,
    current_period_end timestamptz,
    cancelled_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    unique (organization_id, product_code),

    constraint subscriptions_plan_fk
        foreign key (product_code, plan_code)
        references billing.plans(product_code, plan_code),

    constraint subscriptions_status_check
        check (status in ('trial', 'active', 'past_due', 'cancelled', 'expired', 'manual')),

    constraint subscriptions_provider_check
        check (provider in ('manual', 'stripe', 'payu', 'przelewy24', 'tpay'))
);

create index billing_subscriptions_organization_status_idx
on billing.subscriptions (organization_id, status, updated_at desc);

create index billing_subscriptions_product_plan_idx
on billing.subscriptions (product_code, plan_code);

grant select, insert, update, delete on billing.plans to platform_backend_role;
grant select, insert, update, delete on billing.subscriptions to platform_backend_role;

grant select on billing.plans to authenticated;
grant select on billing.subscriptions to authenticated;

alter table billing.plans enable row level security;
alter table billing.subscriptions enable row level security;

create policy billing_plans_select_active
on billing.plans
for select
to authenticated
using (active);

create policy billing_subscriptions_select_member
on billing.subscriptions
for select
to authenticated
using (platform.is_org_member(organization_id, (select auth.uid())));

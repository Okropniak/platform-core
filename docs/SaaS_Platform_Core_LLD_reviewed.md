# SaaS Platform Core — Low Level Design (LLD)

Version: 1.0  
Target stack: Supabase, PostgreSQL, Java Spring Boot Platform Core, React frontends, PrimeReact UI, independent SaaS backends

---

# 1. Purpose

This document defines the implementation-level design for a central SaaS Platform Core.

The platform supports multiple independent SaaS products using:

- one Supabase project,
- one Supabase Auth instance,
- one shared PostgreSQL database,
- separate PostgreSQL schemas per SaaS,
- central platform schemas for tenants, products, billing, entitlements, and usage.

The goal is to allow a small team to launch many SaaS products without duplicating authentication, billing, subscriptions, entitlements, and usage metering.

---
# 1.1 Technology and Implementation Constraints

## Platform Core

Platform Core must be implemented as a modular monolith written in Java using Spring Boot.

Platform Core is responsible for:

- tenant and organization management,
- product registry,
- product registration,
- product access,
- billing provider integration,
- subscriptions,
- entitlements,
- usage metering,
- quota enforcement,
- audit events,
- administrative APIs.

The modular monolith should contain clear internal modules, for example:

```text
platform-core
├── identity
├── tenants
├── products
├── billing
├── entitlements
├── usage
├── audit
└── admin
```

The modules should have explicit boundaries, but they are deployed as one application.

This avoids premature microservice complexity while preserving the option to extract selected modules later.

## Frontend

All frontends must be written in React.

The default UI component library is PrimeReact:

```text
https://primereact.org/
```

Custom UI controls are not allowed unless explicitly approved.

The UI should be composed from PrimeReact components and standard layouts.

## Styling Rules

Styles must be controlled through CSS classes or utility classes.

Inline styles should be avoided.

Do not create custom UI controls only to achieve styling differences.

Preferred order:

```text
PrimeReact component
↓
PrimeReact props
↓
CSS class / utility class
↓
shared theme variables
```

Avoid:

```text
inline style objects
ad-hoc custom components replacing PrimeReact controls
screen-specific uncontrolled CSS hacks
```

## UI Design Source

Initial UI concepts should be created in Google Stitch:

```text
https://stitch.withgoogle.com/
```

The implementation model must not invent a custom visual design system.

The implementation model should prepare prompts for Google Stitch and ask the user to create or refine the GUI project there.

The model should then implement the React screens based on the GUI prepared in Stitch, using PrimeReact components and CSS classes.

## UI Prompting Rule

When a new screen or flow is needed, the model must first prepare a prompt for Google Stitch.

The model should ask the user to generate or refine the design in Stitch before implementation.

The model should not directly implement custom UI controls or arbitrary custom styling.

---


# 2. Core Architectural Rule

The platform must remain product-agnostic.

The platform may know:

```text
organization_id
user_id
product_code
feature_code
metric_code
amount
period
subscription_status
```

The platform must not know:

```text
saas implementation
LLM prompts
RAG details
business-specific SaaS data
AI model behavior
domain-specific workflows
```

Each SaaS backend owns its own domain logic.

---

# 3. Schema Layout

```sql
create schema if not exists platform;
create schema if not exists billing;
create schema if not exists entitlement;
create schema if not exists usage;
create schema if not exists audit;

-- Example SaaS schemas
create schema if not exists search_saas;
create schema if not exists grant_saas;
create schema if not exists architecture_saas;
```

Supabase built-in schemas:

```text
auth
storage
public
```

Recommended rule:

- `auth` is owned by Supabase Auth.
- `platform`, `billing`, `entitlement`, `usage`, `audit` are owned by SaaS Platform Core.
- each product schema is owned by its SaaS backend.

---

# 4. Platform Schema

## 4.1 profiles

```sql
create table platform.profiles (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null unique references auth.users(id) on delete cascade,
    display_name text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
```

Purpose:

- stores platform-level profile data,
- does not replace `auth.users`,
- avoids putting application metadata directly into Supabase Auth.

---

## 4.2 organizations

```sql
create table platform.organizations (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    type text not null default 'individual',
    created_by uuid references auth.users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint organizations_type_check
        check (type in ('individual', 'company'))
);
```

Purpose:

- represents tenant/customer/workspace,
- every billable SaaS relationship is attached to an organization.

---

## 4.3 organization_members

```sql
create table platform.organization_members (
    organization_id uuid not null references platform.organizations(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    role text not null default 'member',
    status text not null default 'active',
    created_at timestamptz not null default now(),

    primary key (organization_id, user_id),

    constraint organization_members_role_check
        check (role in ('owner', 'admin', 'member')),

    constraint organization_members_status_check
        check (status in ('active', 'invited', 'disabled'))
);
```

Purpose:

- answers: "Does this user belong to this organization?"
- base for tenant isolation.

---

## 4.4 products

```sql
create table platform.products (
    id uuid primary key default gen_random_uuid(),
    code text not null unique,
    name text not null,
    status text not null default 'active',
    created_at timestamptz not null default now(),

    constraint products_status_check
        check (status in ('active', 'disabled', 'archived'))
);
```

Example data:

```sql
insert into platform.products(code, name)
values
  ('search_saas', 'Search SaaS'),
  ('grant_saas', 'Grant SaaS'),
  ('architecture_saas', 'Architecture SaaS');
```

---

## 4.5 product_registrations

```sql
create table platform.product_registrations (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references platform.organizations(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    product_code text not null references platform.products(code),
    accepted_terms_version text,
    accepted_privacy_version text,
    status text not null default 'active',
    registered_at timestamptz not null default now(),

    unique (organization_id, user_id, product_code),

    constraint product_registrations_status_check
        check (status in ('active', 'disabled', 'revoked'))
);
```

Purpose:

- supports legal/product-specific registration,
- user may have a global identity but not be registered to every SaaS.

---

## 4.6 product_access

```sql
create table platform.product_access (
    organization_id uuid not null references platform.organizations(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    product_code text not null references platform.products(code),
    role text not null default 'user',
    enabled boolean not null default true,
    created_at timestamptz not null default now(),

    primary key (organization_id, user_id, product_code),

    constraint product_access_role_check
        check (role in ('owner', 'admin', 'user', 'viewer'))
);
```

Purpose:

- answers: "Can this user access this product for this organization?"
- separate from product registration and subscription.

---

# 5. Billing Schema

## 5.1 plans

```sql
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
```

Example:

```sql
insert into billing.plans(product_code, plan_code, name, price, currency)
values
  ('search_saas', 'free', 'Free', 0, 'USD'),
  ('search_saas', 'pro', 'Pro', 29, 'USD');
```

---

## 5.2 subscriptions

```sql
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
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    unique (organization_id, product_code),

    constraint subscriptions_status_check
        check (status in ('trial', 'active', 'past_due', 'cancelled', 'expired', 'manual')),

    constraint subscriptions_provider_check
        check (provider in ('manual', 'stripe', 'payu', 'przelewy24', 'tpay'))
);
```

Purpose:

- stores commercial status,
- does not directly grant access,
- subscription changes should trigger entitlement synchronization.

---

# 6. Entitlement Schema

## 6.1 features

```sql
create table entitlement.features (
    id uuid primary key default gen_random_uuid(),
    product_code text not null references platform.products(code),
    feature_code text not null,
    name text not null,
    description text,
    active boolean not null default true,

    unique (product_code, feature_code)
);
```

Examples:

```sql
insert into entitlement.features(product_code, feature_code, name)
values
  ('search_saas', 'basic_search', 'Basic Search'),
  ('search_saas', 'ai_search_per_use', 'AI Search Per Use'),
  ('search_saas', 'ai_search_tokens', 'AI Search Token Based');
```

---

## 6.2 metrics

```sql
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
```

Examples:

```sql
insert into entitlement.metrics(product_code, metric_code, unit, period)
values
  ('search_saas', 'ai_search_usage', 'use', 'monthly'),
  ('search_saas', 'ai_search_tokens', 'token', 'monthly');
```

---

## 6.3 organization_entitlements

```sql
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

    unique (organization_id, product_code, feature_code, metric_code),

    constraint organization_entitlements_source_check
        check (source in ('plan', 'manual', 'promo', 'enterprise'))
);
```

Example:

```text
organization_id | product_code | feature_code        | metric_code       | enabled | limit_value
org_1           | search_saas  | basic_search        | null              | true    | null
org_1           | search_saas  | ai_search_per_use   | ai_search_usage   | true    | 100
org_1           | search_saas  | ai_search_tokens    | ai_search_tokens  | true    | 100000
```
## 6.4 user_entitlements

```sql
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

    unique (organization_id, user_id, product_code, feature_code, metric_code),

    constraint user_entitlements_source_check
        check (source in ('manual', 'policy', 'promo', 'enterprise', 'admin_override'))
);
```

Example:
```text
organization_id | user_id | product_code | feature_code      | metric_code      | enabled | limit_value
org_1           | user_1  | search_saas  | ai_search_tokens  | ai_search_tokens | true    | 30000
org_1           | user_2  | search_saas  | ai_search_tokens  | ai_search_tokens | true    | 50000
```

Purpose:

- defines optional per-user limits within an organization,
- does not replace organization-level entitlements,
- applies only when a user-level override exists.

Effective limit rule:

```text
If no user_entitlement exists:
    use organization_entitlement only.

If user_entitlement exists:
    enforce both organization limit and user limit.
```

# 7. Usage Schema

## 7.1 usage_counters

```sql
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

    constraint usage_counters_scope_check
        check (counter_scope in ('organization', 'user')),

    constraint usage_counters_user_scope_check
        check (
            (counter_scope = 'organization' and user_id is null)
            or
            (counter_scope = 'user' and user_id is not null)
        )
);
```

Recommended unique indexes:

```sql
create unique index usage_counters_org_unique
on usage.usage_counters (
    organization_id,
    product_code,
    metric_code,
    period_start,
    period_end
)
where counter_scope = 'organization';
```

```sql
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
```

These indexes are required because the table supports both organization-level counters and user-level counters.

Purpose:

- fast quota checks,
- avoids aggregating event history on every request.

---

## 7.2 usage_reservations

```sql
create table usage.usage_reservations (
    id uuid primary key default gen_random_uuid(),

    organization_id uuid not null references platform.organizations(id) on delete cascade,
    user_id uuid references auth.users(id) on delete cascade,

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

    constraint usage_reservations_status_check
        check (status in ('reserved', 'finalized', 'cancelled', 'expired')),

    constraint usage_reservations_scope_check
        check (counter_scope in ('organization', 'user', 'organization_and_user')),

    constraint usage_reservations_user_scope_check
        check (
            (counter_scope = 'organization' and user_id is null)
            or
            (counter_scope in ('user', 'organization_and_user') and user_id is not null)
        )
);
```


Recommended idempotency index:

```sql
create unique index usage_reservations_idempotency_unique
on usage.usage_reservations (
    organization_id,
    product_code,
    metric_code,
    idempotency_key
);
```

This prevents double reservation for the same billable operation.

Purpose:

- supports token-based or estimated workloads,
- prevents exceeding limit during concurrent calls.

---

## 7.3 usage_events

```sql
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

    constraint usage_events_event_type_check
        check (event_type in ('consume', 'reserve', 'finalize', 'release', 'cancel')),

    constraint usage_events_scope_check
        check (counter_scope in ('organization', 'user', 'organization_and_user'))
);
```


Recommended indexes:

```sql
create index usage_events_org_product_metric_created_idx
on usage.usage_events (
    organization_id,
    product_code,
    metric_code,
    created_at desc
);
```

```sql
create index usage_events_user_product_metric_created_idx
on usage.usage_events (
    user_id,
    product_code,
    metric_code,
    created_at desc
);
```

```sql
create index usage_events_reservation_idx
on usage.usage_events (reservation_id);
```

In `usage_events`, `user_id` represents the actor who triggered the operation, not necessarily the scope of the counter.

Rule:

- do not store prompts, personal content, or SaaS domain payloads in usage metadata.

---

# 8. Audit Schema

```sql
create table audit.audit_events (
    id bigserial primary key,
    organization_id uuid,
    user_id uuid,
    product_code text,
    event_type text not null,
    entity_type text,
    entity_id text,
    created_at timestamptz not null default now(),
    metadata jsonb not null default '{}'::jsonb
);
```

Examples:

```text
product_registered
subscription_created
entitlement_changed
usage_limit_exceeded
user_added_to_organization
```

---

# 9. RLS Strategy

## 9.1 Helper Function

```sql
create or replace function platform.is_org_member(p_organization_id uuid)
returns boolean
language sql
security definer
stable
as $$
    select exists (
        select 1
        from platform.organization_members om
        where om.organization_id = p_organization_id
          and om.user_id = auth.uid()
          and om.status = 'active'
    );
$$;
```

## 9.2 Helper Function for Product Access

```sql
create or replace function platform.has_product_access(
    p_organization_id uuid,
    p_product_code text
)
returns boolean
language sql
security definer
stable
as $$
    select exists (
        select 1
        from platform.product_access pa
        where pa.organization_id = p_organization_id
          and pa.user_id = auth.uid()
          and pa.product_code = p_product_code
          and pa.enabled = true
    );
$$;
```

---

# 10. Example Product Table and RLS

## 10.1 search_saas.search_queries

```sql
create table search_saas.search_queries (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references platform.organizations(id) on delete cascade,
    user_id uuid references auth.users(id),
    query text not null,
    search_type text not null,
    created_at timestamptz not null default now()
);
```

## 10.2 Enable RLS

```sql
alter table search_saas.search_queries enable row level security;
```

## 10.3 Select Policy

```sql
create policy "search queries visible to product members"
on search_saas.search_queries
for select
using (
    platform.is_org_member(organization_id)
    and platform.has_product_access(organization_id, 'search_saas')
);
```

## 10.4 Insert Policy

```sql
create policy "search queries insertable by product members"
on search_saas.search_queries
for insert
with check (
    platform.is_org_member(organization_id)
    and platform.has_product_access(organization_id, 'search_saas')
);
```

---

# 11. Platform Operations and Database Functions

Platform Core is implemented as a Java Spring Boot modular monolith.

The SQL functions below should be treated as database-level operations used internally by Platform Core, not necessarily as public client-facing RPC endpoints.

Recommended exposure model:

```text
React Frontend
    ↓
SaaS Backend or Platform Admin Frontend
    ↓
Platform Core API - Spring Boot
    ↓
Supabase/PostgreSQL functions and tables
```

Do not expose billing, entitlement, or usage write operations directly to public frontend clients.

The SQL functions in this section require a follow-up update to fully support `user_entitlements` and user-level usage counters.

## 11.1 check_product_access

```sql
create or replace function platform.check_product_access(
    p_organization_id uuid,
    p_product_code text
)
returns jsonb
language plpgsql
security definer
as $$
declare
    v_allowed boolean;
    v_role text;
begin
    select pa.enabled, pa.role
    into v_allowed, v_role
    from platform.product_access pa
    where pa.organization_id = p_organization_id
      and pa.user_id = auth.uid()
      and pa.product_code = p_product_code;

    return jsonb_build_object(
        'allowed', coalesce(v_allowed, false),
        'role', v_role
    );
end;
$$;
```

---

## 11.2 get_entitlements

```sql
create or replace function entitlement.get_entitlements(
    p_organization_id uuid,
    p_product_code text
)
returns jsonb
language sql
security definer
stable
as $$
    select jsonb_object_agg(
        feature_code,
        jsonb_build_object(
            'enabled', enabled,
            'metric_code', metric_code,
            'limit_value', limit_value,
            'period', period
        )
    )
    from entitlement.organization_entitlements
    where organization_id = p_organization_id
      and product_code = p_product_code
      and enabled = true
      and (valid_until is null or valid_until > now());
$$;
```

---

## 11.3 consume_usage

For per-use metering.

```sql
create or replace function usage.consume_usage(
    p_organization_id uuid,
    p_product_code text,
    p_metric_code text,
    p_amount numeric,
    p_idempotency_key text
)
returns jsonb
language plpgsql
security definer
as $$
declare
    v_limit numeric;
    v_used numeric;
    v_reserved numeric;
    v_period_start timestamptz;
    v_period_end timestamptz;
begin
    v_period_start := date_trunc('month', now());
    v_period_end := v_period_start + interval '1 month';

    select oe.limit_value
    into v_limit
    from entitlement.organization_entitlements oe
    where oe.organization_id = p_organization_id
      and oe.product_code = p_product_code
      and oe.metric_code = p_metric_code
      and oe.enabled = true
      and (oe.valid_until is null or oe.valid_until > now())
    limit 1;

    if v_limit is null then
        return jsonb_build_object('accepted', false, 'reason', 'no_entitlement');
    end if;

    insert into usage.usage_counters(
        organization_id, product_code, metric_code,
        period_start, period_end, used_value, reserved_value
    )
    values (
        p_organization_id, p_product_code, p_metric_code,
        v_period_start, v_period_end, 0, 0
    )
    on conflict (organization_id, product_code, metric_code, period_start, period_end)
    do nothing;

    select used_value, reserved_value
    into v_used, v_reserved
    from usage.usage_counters
    where organization_id = p_organization_id
      and product_code = p_product_code
      and metric_code = p_metric_code
      and period_start = v_period_start
      and period_end = v_period_end
    for update;

    if v_used + v_reserved + p_amount > v_limit then
        return jsonb_build_object(
            'accepted', false,
            'reason', 'limit_exceeded',
            'limit', v_limit,
            'used', v_used,
            'reserved', v_reserved
        );
    end if;

    update usage.usage_counters
    set used_value = used_value + p_amount,
        updated_at = now()
    where organization_id = p_organization_id
      and product_code = p_product_code
      and metric_code = p_metric_code
      and period_start = v_period_start
      and period_end = v_period_end;

    insert into usage.usage_events(
        organization_id, user_id, product_code, metric_code,
        amount, event_type, idempotency_key
    )
    values (
        p_organization_id, auth.uid(), p_product_code, p_metric_code,
        p_amount, 'consume', p_idempotency_key
    );

    return jsonb_build_object(
        'accepted', true,
        'limit', v_limit,
        'used', v_used + p_amount,
        'remaining', v_limit - (v_used + p_amount)
    );
end;
$$;
```

---

## 11.4 reserve_usage

For estimated usage.

```sql
create or replace function usage.reserve_usage(
    p_organization_id uuid,
    p_product_code text,
    p_metric_code text,
    p_amount numeric,
    p_idempotency_key text
)
returns jsonb
language plpgsql
security definer
as $$
declare
    v_limit numeric;
    v_used numeric;
    v_reserved numeric;
    v_period_start timestamptz;
    v_period_end timestamptz;
    v_reservation_id uuid;
begin
    v_period_start := date_trunc('month', now());
    v_period_end := v_period_start + interval '1 month';

    select oe.limit_value
    into v_limit
    from entitlement.organization_entitlements oe
    where oe.organization_id = p_organization_id
      and oe.product_code = p_product_code
      and oe.metric_code = p_metric_code
      and oe.enabled = true
      and (oe.valid_until is null or oe.valid_until > now())
    limit 1;

    if v_limit is null then
        return jsonb_build_object('accepted', false, 'reason', 'no_entitlement');
    end if;

    insert into usage.usage_counters(
        organization_id, product_code, metric_code,
        period_start, period_end, used_value, reserved_value
    )
    values (
        p_organization_id, p_product_code, p_metric_code,
        v_period_start, v_period_end, 0, 0
    )
    on conflict (organization_id, product_code, metric_code, period_start, period_end)
    do nothing;

    select used_value, reserved_value
    into v_used, v_reserved
    from usage.usage_counters
    where organization_id = p_organization_id
      and product_code = p_product_code
      and metric_code = p_metric_code
      and period_start = v_period_start
      and period_end = v_period_end
    for update;

    if v_used + v_reserved + p_amount > v_limit then
        return jsonb_build_object(
            'accepted', false,
            'reason', 'limit_exceeded',
            'limit', v_limit,
            'used', v_used,
            'reserved', v_reserved
        );
    end if;

    update usage.usage_counters
    set reserved_value = reserved_value + p_amount,
        updated_at = now()
    where organization_id = p_organization_id
      and product_code = p_product_code
      and metric_code = p_metric_code
      and period_start = v_period_start
      and period_end = v_period_end;

    insert into usage.usage_reservations(
        organization_id, user_id, product_code, metric_code,
        reserved_amount, idempotency_key
    )
    values (
        p_organization_id, auth.uid(), p_product_code, p_metric_code,
        p_amount, p_idempotency_key
    )
    returning id into v_reservation_id;

    insert into usage.usage_events(
        organization_id, user_id, product_code, metric_code,
        amount, event_type, idempotency_key
    )
    values (
        p_organization_id, auth.uid(), p_product_code, p_metric_code,
        p_amount, 'reserve', p_idempotency_key
    );

    return jsonb_build_object(
        'accepted', true,
        'reservation_id', v_reservation_id,
        'reserved', p_amount,
        'remaining_after_reservation', v_limit - (v_used + v_reserved + p_amount)
    );
end;
$$;
```

---

## 11.5 finalize_usage

```sql
create or replace function usage.finalize_usage(
    p_reservation_id uuid,
    p_actual_amount numeric
)
returns jsonb
language plpgsql
security definer
as $$
declare
    r usage.usage_reservations%rowtype;
    v_period_start timestamptz;
    v_period_end timestamptz;
    v_release numeric;
begin
    select *
    into r
    from usage.usage_reservations
    where id = p_reservation_id
      and status = 'reserved'
    for update;

    if not found then
        return jsonb_build_object('finalized', false, 'reason', 'reservation_not_found');
    end if;

    v_period_start := date_trunc('month', r.created_at);
    v_period_end := v_period_start + interval '1 month';
    v_release := r.reserved_amount - p_actual_amount;

    update usage.usage_counters
    set reserved_value = reserved_value - r.reserved_amount,
        used_value = used_value + p_actual_amount,
        updated_at = now()
    where organization_id = r.organization_id
      and product_code = r.product_code
      and metric_code = r.metric_code
      and period_start = v_period_start
      and period_end = v_period_end;

    update usage.usage_reservations
    set status = 'finalized',
        actual_amount = p_actual_amount,
        finalized_at = now()
    where id = p_reservation_id;

    insert into usage.usage_events(
        organization_id, user_id, product_code, metric_code,
        amount, event_type, idempotency_key
    )
    values (
        r.organization_id, r.user_id, r.product_code, r.metric_code,
        p_actual_amount, 'finalize', r.idempotency_key
    );

    return jsonb_build_object(
        'finalized', true,
        'actual_amount', p_actual_amount,
        'released_amount', v_release
    );
end;
$$;
```

---

# 12. SaaS Backend API Contract

Each SaaS backend should expose domain APIs but use Platform Core APIs internally. Platform Core is a Spring Boot modular monolith. It may execute PostgreSQL functions internally, but SaaS backends should not directly modify platform tables.

Example for Search SaaS:

```text
POST /search/basic
POST /search/ai-per-use
POST /search/ai-token-based
GET  /me/access
GET  /me/usage
```

---

# 13. AI Search Per-Use Flow

```text
Frontend
  ↓
Search SaaS Backend
  ↓
platform.check_product_access()
  ↓
usage.consume_usage(metric=ai_search_usage, amount=1)
  ↓
execute AI search
  ↓
return result
```

If usage is denied, do not execute the AI call.

---

# 14. AI Search Token-Based Flow

```text
Frontend
  ↓
Search SaaS Backend
  ↓
estimate tokens
  ↓
usage.reserve_usage(metric=ai_search_tokens, amount=estimated)
  ↓
execute AI call
  ↓
read actual token usage
  ↓
usage.finalize_usage(actual_amount)
  ↓
return result
```

If finalization fails, backend must log the event and retry.

---

# 15. Idempotency

Every billable operation must pass an idempotency key.

Recommended key format:

```text
{product_code}:{operation}:{request_uuid}
```

Examples:

```text
search_saas:ai_per_use:7d0c6dd8
search_saas:ai_tokens:8fbc2f23
```

Purpose:

- prevents double billing,
- supports retry logic,
- protects against frontend resubmits.

---

# 16. Service Roles and Permissions

Recommended technical roles:

```text
platform_backend_role
search_saas_backend_role
grant_saas_backend_role
architecture_saas_backend_role
```

Access principle:

```text
SaaS backend can access its own schema.
SaaS backend can execute platform RPCs.
SaaS backend cannot directly modify billing, entitlements, or usage tables.
```

In early MVP, this can be simplified using backend service role, but the long-term direction should be privilege separation.

---

# 17. Frontend Integration

Each React app:

1. Uses Supabase Auth.
2. Reads current session.
3. Calls SaaS backend.
4. SaaS backend verifies platform access.
5. UI displays:
   - access denied,
   - registration required,
   - paywall,
   - quota exceeded,
   - normal product experience.

Recommended routing:

```text
saas1.example.com
saas2.example.com
saas3.example.com
```


UI implementation constraints:

- React is mandatory.
- PrimeReact is the mandatory component library.
- Custom UI controls are not allowed by default.
- Google Stitch is the source for initial GUI concepts.
- The implementation model should prepare prompts for Stitch before implementing screens.
- Styles must be controlled through CSS classes or utility classes.
- Inline styles should be avoided.

Example Stitch prompt template:

```text
Design a SaaS admin screen for managing organization entitlements and usage limits.
Use a clean B2B dashboard style.
Include tables for products, features, limits, used quota, remaining quota, and user-level overrides.
Prepare the layout for implementation in React using PrimeReact components.
Avoid custom controls and use standard components such as DataTable, Card, Dialog, Button, Dropdown, InputText, Tag, and Toast.
Use CSS classes for styling and avoid inline styles.
```


---

# 18. Product Onboarding Checklist

For every new SaaS:

## Database

```sql
create schema new_saas;
```

## Platform

```sql
insert into platform.products(code, name)
values ('new_saas', 'New SaaS');
```

## Features

```sql
insert into entitlement.features(product_code, feature_code, name)
values
  ('new_saas', 'basic_access', 'Basic Access');
```

## Metrics

```sql
insert into entitlement.metrics(product_code, metric_code, unit, period)
values
  ('new_saas', 'api_calls', 'request', 'monthly');
```

## Plans

```sql
insert into billing.plans(product_code, plan_code, name, price)
values
  ('new_saas', 'free', 'Free', 0),
  ('new_saas', 'pro', 'Pro', 29);
```

## Backend

Set:

```text
PRODUCT_CODE=new_saas
```

Use platform RPCs.

---

# 19. Repository Structure

Recommended:

```text
repo-platform-core
  /backend-spring-boot
    /src/main/java
    /src/main/resources
  /supabase
    /migrations
    /schemas
    /functions
  /frontend-admin-react
  /docs

repo-search-saas
  /frontend
  /backend
  /database

repo-grant-saas
  /frontend
  /backend
  /database
```

Alternative monorepo:

```text
saas-portfolio
  /platform
  /products/search
  /products/grant
  /infra
  /docs
```

For a small team, monorepo may be simpler.

---

# 20. Migration Strategy

## Current State

```text
One Supabase Project
Many Schemas
```

## Future Extraction

When one SaaS grows:

```text
platform-prod
  platform
  billing
  entitlement
  usage

search-prod
  saas1_saas
```

The SaaS backend then connects to:

```text
Platform DB for platform data
Product DB for domain data
```

Frontend remains unchanged.

---

# 21. When to Split a Product

Split a SaaS into a separate Supabase project when at least one is true:

- it generates enough revenue to justify additional cost,
- it has high workload and affects other products,
- it requires different backup/restore policy,
- it requires stronger compliance isolation,
- it has a separate team and release cadence,
- it needs custom database extensions or tuning.

Do not split by default.

---

# 22. Billing Provider Integration

## Provider Abstraction

Internal model:

```text
provider = stripe | payu | przelewy24 | tpay | manual
```

Webhook flow:

```text
Provider Webhook
  ↓
Billing Handler
  ↓
billing.subscriptions
  ↓
Entitlement Sync
  ↓
organization_entitlements
```

The product must never call payment provider directly to determine access.

---

# 23. Entitlement Sync

When subscription changes:

```text
subscription active
  ↓
read plan
  ↓
generate organization_entitlements
```

When subscription cancelled:

```text
subscription cancelled
  ↓
disable paid entitlements
  ↓
keep free entitlements if applicable
```

---

# 24. Operational Monitoring

Track per product:

- usage consumption,
- quota denials,
- active organizations,
- active users,
- failed usage finalizations,
- slow queries,
- expensive queries,
- database size,
- failed webhook events.

---

# 25. Main Risks

## Shared Blast Radius

One database serves all products.

Mitigation:

- schema boundaries,
- RLS,
- strict migration review,
- monitoring,
- performance tests.

## Platform Coupling

Too much product logic may leak into platform.

Mitigation:

- product_code / feature_code / metric_code abstraction,
- no domain-specific tables in platform,
- no SaaS prompt/query data in platform.

## Usage Metering Errors

Incorrect metering may cause billing disputes.

Mitigation:

- idempotency,
- usage_events,
- audit log,
- reserve/finalize pattern,
- reconciliation jobs.

---

# 26. MVP Implementation Order

## Sprint 1 — Core Identity and Tenancy

- profiles
- organizations
- organization_members
- RLS helpers

## Sprint 2 — Product Access

- products
- product_registrations
- product_access
- check_product_access()

## Sprint 3 — Entitlements

- features
- metrics
- organization_entitlements
- get_entitlements()

## Sprint 4 — Usage

- usage_counters
- usage_reservations
- usage_events
- consume_usage()
- reserve_usage()
- finalize_usage()

## Sprint 5 — First Product

- search_saas schema
- basic search
- per-use AI search
- token-based AI search

## Sprint 6 — Billing

- plans
- subscriptions
- manual billing first
- Stripe/PayU abstraction later

## Sprint 7 — Admin Portal

- organizations
- products
- subscriptions
- entitlements
- usage dashboard

---

# 27. Final Implementation Principle

The SaaS backend owns product behavior.

The platform owns commercial and access rules.

```text
SaaS Backend:
- search
- AI
- prompts
- documents
- domain data

Platform Core:
- identity
- organizations
- product access
- subscriptions
- entitlements
- usage
- quotas
```


---

# 28. UI Generation Workflow

For every new screen or user flow:

1. The implementation model prepares a Google Stitch prompt.
2. The user generates or refines the screen in Stitch.
3. The model maps the approved design to React.
4. The model uses PrimeReact components only.
5. The model controls styles through CSS classes or utility classes.
6. The model avoids inline styles.
7. The model avoids custom controls unless explicitly approved.

The preferred implementation mapping is:

```text
Stitch design
    ↓
React screen
    ↓
PrimeReact components
    ↓
CSS classes / utility classes
    ↓
Platform Core / SaaS Backend APIs
```

The model should not start by writing custom CSS-heavy UI.

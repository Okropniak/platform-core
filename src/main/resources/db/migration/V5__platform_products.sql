create table platform.products (
    id uuid primary key default gen_random_uuid(),
    code text not null unique,
    name text not null,
    status text not null default 'active',
    created_at timestamptz not null default now(),

    constraint products_status_check
        check (status in ('active', 'disabled', 'archived'))
);

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

create index product_registrations_user_idx
    on platform.product_registrations (user_id, product_code)
    where status = 'active';

create index product_registrations_organization_idx
    on platform.product_registrations (organization_id, product_code)
    where status = 'active';

create index product_access_user_idx
    on platform.product_access (user_id, product_code)
    where enabled;

grant select, insert, update, delete on platform.products to platform_backend_role;
grant select, insert, update, delete on platform.product_registrations to platform_backend_role;
grant select, insert, update, delete on platform.product_access to platform_backend_role;

grant select on platform.products to authenticated;
grant select on platform.product_registrations to authenticated;
grant select on platform.product_access to authenticated;

alter table platform.product_registrations enable row level security;
alter table platform.product_access enable row level security;

create policy product_registrations_select_member
on platform.product_registrations
for select
to authenticated
using (platform.is_org_member(organization_id, (select auth.uid())));

create policy product_access_select_member
on platform.product_access
for select
to authenticated
using (platform.is_org_member(organization_id, (select auth.uid())));

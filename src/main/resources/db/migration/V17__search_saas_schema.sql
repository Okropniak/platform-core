create schema if not exists search_saas;

do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'search_saas_backend_role') then
        create role search_saas_backend_role;
    end if;
end
$$;

grant usage on schema search_saas to platform_backend_role;
grant usage on schema search_saas to search_saas_backend_role;
grant usage on schema search_saas to authenticated;

create table search_saas.search_queries (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references platform.organizations(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    query text not null,
    search_type text not null,
    created_at timestamptz not null default now(),

    constraint search_queries_search_type_check
        check (search_type in ('basic', 'ai_per_use', 'ai_tokens')),

    constraint search_queries_query_not_blank_check
        check (length(trim(query)) > 0)
);

create index search_queries_organization_created_idx
on search_saas.search_queries (organization_id, created_at desc);

create index search_queries_user_created_idx
on search_saas.search_queries (user_id, created_at desc);

grant select, insert, update, delete on search_saas.search_queries to platform_backend_role;
grant select, insert, update, delete on search_saas.search_queries to search_saas_backend_role;
grant select, insert on search_saas.search_queries to authenticated;

alter table search_saas.search_queries enable row level security;

create policy search_queries_backend_access
on search_saas.search_queries
for all
to platform_backend_role, search_saas_backend_role
using (true)
with check (true);

create policy search_queries_select_product_member
on search_saas.search_queries
for select
to authenticated
using (
    platform.is_org_member(organization_id, (select auth.uid()))
    and platform.has_product_access(organization_id, (select auth.uid()), 'search_saas')
);

create policy search_queries_insert_product_member
on search_saas.search_queries
for insert
to authenticated
with check (
    user_id = (select auth.uid())
    and platform.is_org_member(organization_id, (select auth.uid()))
    and platform.has_product_access(organization_id, (select auth.uid()), 'search_saas')
);

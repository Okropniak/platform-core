create or replace function platform.is_org_member(
    p_organization_id uuid,
    p_user_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = platform, pg_temp
as $$
    select exists (
        select 1
        from platform.organization_members om
        where om.organization_id = p_organization_id
          and om.user_id = p_user_id
          and om.status = 'active'
    );
$$;

revoke all on function platform.is_org_member(uuid, uuid) from public;
grant execute on function platform.is_org_member(uuid, uuid) to platform_backend_role;

alter table platform.profiles enable row level security;
alter table platform.organizations enable row level security;
alter table platform.organization_members enable row level security;

create policy profiles_select_own
on platform.profiles
for select
using (user_id = (select auth.uid()));

create policy organizations_select_member
on platform.organizations
for select
using (platform.is_org_member(id, (select auth.uid())));

create policy organization_members_select_member
on platform.organization_members
for select
using (platform.is_org_member(organization_id, (select auth.uid())));

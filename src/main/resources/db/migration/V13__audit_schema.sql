create table audit.audit_events (
    id bigserial primary key,
    organization_id uuid references platform.organizations(id) on delete set null,
    user_id uuid references auth.users(id) on delete set null,
    product_code text references platform.products(code),
    event_type text not null,
    entity_type text,
    entity_id text,
    created_at timestamptz not null default now(),
    metadata jsonb not null default '{}'::jsonb
);

create index audit_events_organization_created_idx
on audit.audit_events (organization_id, created_at desc);

create index audit_events_product_type_created_idx
on audit.audit_events (product_code, event_type, created_at desc);

create index audit_events_user_created_idx
on audit.audit_events (user_id, created_at desc);

grant select, insert, update, delete on audit.audit_events to platform_backend_role;
grant usage, select on all sequences in schema audit to platform_backend_role;

grant select on audit.audit_events to authenticated;

alter table audit.audit_events enable row level security;

create policy audit_events_select_member
on audit.audit_events
for select
to authenticated
using (
    organization_id is not null
    and platform.is_org_member(organization_id, (select auth.uid()))
);

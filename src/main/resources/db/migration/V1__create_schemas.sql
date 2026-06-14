create schema if not exists platform;
create schema if not exists billing;
create schema if not exists entitlement;
create schema if not exists usage;
create schema if not exists audit;

do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'platform_backend_role') then
        create role platform_backend_role;
    end if;
end
$$;

grant usage on schema platform, billing, entitlement, usage, audit to platform_backend_role;

create table if not exists platform.event_publication (
    id uuid primary key,
    publication_date timestamptz not null,
    listener_id text not null,
    serialized_event text not null,
    event_type text not null,
    completion_date timestamptz,
    last_resubmission_date timestamptz,
    completion_attempts integer not null default 0,
    status text
);

create table if not exists platform.event_publication_archive (
    id uuid primary key,
    publication_date timestamptz not null,
    listener_id text not null,
    serialized_event text not null,
    event_type text not null,
    completion_date timestamptz,
    last_resubmission_date timestamptz,
    completion_attempts integer not null default 0,
    status text
);

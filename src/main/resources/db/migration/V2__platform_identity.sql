create table platform.profiles (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null unique references auth.users(id) on delete cascade,
    display_name text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

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

create index organization_members_user_status_idx
on platform.organization_members (user_id, status);

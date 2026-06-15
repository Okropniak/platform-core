create schema if not exists auth;

create table if not exists auth.users (
    id uuid primary key,
    raw_user_meta_data jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create or replace function auth.uid()
returns uuid
language plpgsql
stable
as $$
declare
    v_subject text;
begin
    v_subject := current_setting('request.jwt.claim.sub', true);

    if v_subject is null or v_subject = '' then
        raise exception 'test auth.uid() called without request.jwt.claim.sub';
    end if;

    return v_subject::uuid;
end;
$$;

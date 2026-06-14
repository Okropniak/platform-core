create or replace function platform.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = platform, auth, pg_temp
as $$
begin
    insert into platform.profiles(user_id, display_name)
    values (new.id, new.raw_user_meta_data->>'display_name')
    on conflict (user_id) do nothing;

    return new;
end;
$$;

revoke all on function platform.handle_new_user() from public;
grant execute on function platform.handle_new_user() to platform_backend_role;

drop trigger if exists on_auth_user_created on auth.users;

create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function platform.handle_new_user();

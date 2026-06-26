-- Uruchom ten skrypt ręcznie w Supabase Dashboard -> SQL Editor.
-- Flyway nie zarządza triggerami na auth.users w hostowanym Supabase,
-- ponieważ właścicielem tej tabeli jest wewnętrzna rola Supabase Auth.

begin;

drop trigger if exists platform_core_on_auth_user_created on auth.users;

create trigger platform_core_on_auth_user_created
    after insert on auth.users
    for each row
    execute function platform.handle_new_user();

commit;

-- Weryfikacja instalacji:
select
    trigger_name,
    event_manipulation,
    action_timing,
    action_statement
from information_schema.triggers
where event_object_schema = 'auth'
  and event_object_table = 'users'
  and trigger_name = 'platform_core_on_auth_user_created';

-- Wycofanie triggera, jeżeli rejestracja użytkowników zacznie zwracać błędy:
-- drop trigger if exists platform_core_on_auth_user_created on auth.users;

insert into platform.products (code, name)
values
    ('search_saas', 'Search SaaS'),
    ('grant_saas', 'Grant SaaS'),
    ('architecture_saas', 'Architecture SaaS')
on conflict (code) do update
set name = excluded.name,
    status = 'active';

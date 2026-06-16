insert into billing.plans(product_code, plan_code, name, price, currency, billing_period, active)
values
    ('search_saas', 'free', 'Free', 0, 'USD', 'monthly', true),
    ('search_saas', 'pro', 'Pro', 29, 'USD', 'monthly', true)
on conflict (product_code, plan_code) do update
set name = excluded.name,
    price = excluded.price,
    currency = excluded.currency,
    billing_period = excluded.billing_period,
    active = excluded.active;

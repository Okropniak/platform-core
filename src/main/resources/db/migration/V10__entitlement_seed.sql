insert into entitlement.features (product_code, feature_code, name)
values
    ('search_saas', 'basic_search', 'Basic Search'),
    ('search_saas', 'ai_search_per_use', 'AI Search Per Use'),
    ('search_saas', 'ai_search_tokens', 'AI Search Token Based')
on conflict (product_code, feature_code) do update
set name = excluded.name,
    active = true;

insert into entitlement.metrics (product_code, metric_code, unit, period)
values
    ('search_saas', 'ai_search_usage', 'use', 'monthly'),
    ('search_saas', 'ai_search_tokens', 'token', 'monthly')
on conflict (product_code, metric_code) do update
set unit = excluded.unit,
    period = excluded.period;

insert into billing.plan_entitlements (
    product_code,
    plan_code,
    feature_code,
    metric_code,
    enabled,
    limit_value,
    period
)
values
    ('search_saas', 'free', 'basic_search', null, true, null, 'monthly'),
    ('search_saas', 'free', 'ai_search_per_use', 'ai_search_usage', true, 100, 'monthly'),
    ('search_saas', 'pro', 'basic_search', null, true, null, 'monthly'),
    ('search_saas', 'pro', 'ai_search_per_use', 'ai_search_usage', true, 1000, 'monthly'),
    ('search_saas', 'pro', 'ai_search_tokens', 'ai_search_tokens', true, 100000, 'monthly')
on conflict (product_code, plan_code, feature_code, metric_code) do update
set enabled = excluded.enabled,
    limit_value = excluded.limit_value,
    period = excluded.period,
    active = true;

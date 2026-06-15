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

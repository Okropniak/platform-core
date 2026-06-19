alter table search_saas.search_queries
drop constraint search_queries_search_type_check;

alter table search_saas.search_queries
add constraint search_queries_search_type_check
    check (search_type in ('basic_search', 'ai_search_per_use', 'ai_search_tokens'));

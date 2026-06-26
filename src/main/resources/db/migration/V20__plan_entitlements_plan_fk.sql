do $$
begin
    if exists (
        select 1
        from billing.plan_entitlements pe
        left join billing.plans p
          on p.product_code = pe.product_code
         and p.plan_code = pe.plan_code
        where p.id is null
    ) then
        raise exception
            'Cannot add plan_entitlements_plan_fk: billing.plan_entitlements contains rows without a matching billing.plans record.';
    end if;
end
$$;

alter table billing.plan_entitlements
    add constraint plan_entitlements_plan_fk
    foreign key (product_code, plan_code)
    references billing.plans(product_code, plan_code);

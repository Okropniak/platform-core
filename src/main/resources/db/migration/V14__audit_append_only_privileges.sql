alter default privileges in schema audit revoke update, delete on tables from platform_backend_role;

revoke update, delete on audit.audit_events from platform_backend_role;
grant select, insert on audit.audit_events to platform_backend_role;

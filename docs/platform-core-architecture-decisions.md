# Platform Core Architecture Decisions

This file records implementation decisions that must be applied before the
database migrations and Spring modules are built.

## ADR-001: Flyway is the database source of truth

All durable database objects are created through Flyway migrations under
`src/main/resources/db/migration`.

Supabase MCP and the Supabase SQL editor are diagnostic tools only. They may be
used to inspect state or run one-off administrative checks, but not to create
tables, functions, triggers, RLS policies, or indexes that are missing from
Flyway.

## ADR-002: JDBC functions receive user ids explicitly

PostgreSQL functions called by Platform Core must not depend on `auth.uid()`.
Spring JDBC connections do not automatically carry the Supabase JWT claims that
PostgREST uses to resolve `auth.uid()`.

Every platform function that needs the actor must accept `p_user_id uuid`.
Spring controllers extract the authenticated user from `@AuthenticationPrincipal
Jwt` using `jwt.getSubject()` and pass that UUID into services and SQL
functions.

Affected function shapes:

```sql
platform.is_org_member(p_organization_id uuid, p_user_id uuid)
platform.has_product_access(p_organization_id uuid, p_user_id uuid, p_product_code text)
platform.check_product_access(p_organization_id uuid, p_user_id uuid, p_product_code text)
entitlement.get_entitlements(p_organization_id uuid, p_user_id uuid, p_product_code text)
usage.consume_usage(p_organization_id uuid, p_user_id uuid, p_product_code text, p_metric_code text, p_amount numeric, p_idempotency_key text)
usage.reserve_usage(p_organization_id uuid, p_user_id uuid, p_product_code text, p_metric_code text, p_amount numeric, p_idempotency_key text)
usage.finalize_usage(p_reservation_id uuid, p_actual_amount numeric, p_user_id uuid)
```

## ADR-003: Usage metering is idempotent

Every billable operation must provide an idempotency key using:

```text
{productCode}:{operation}:{requestUuid}
```

`usage.usage_events` must have a unique index on
`(organization_id, product_code, metric_code, idempotency_key)` where
`idempotency_key is not null`.

`consume_usage` and `reserve_usage` must check for an existing event with the
same key before changing counters. A duplicate key returns the previous result
without incrementing usage or reserved values again.

## ADR-004: Usage counters avoid partial-index conflict ambiguity

`usage.usage_counters` must use a conflict target that PostgreSQL can address
reliably from `ON CONFLICT`. The MVP implementation will use a named unique
constraint that includes `counter_scope` instead of relying on partial unique
indexes as the conflict target.

The table still keeps a check constraint that enforces:

- organization scope has `user_id is null`
- user or organization-and-user scope has `user_id is not null`

## ADR-005: Plans define entitlements through billing.plan_entitlements

`billing.plans` stores commercial plan metadata only. Feature and quota content
for a plan lives in `billing.plan_entitlements`.

`EntitlementSyncService` reads `billing.plan_entitlements` and writes
`entitlement.organization_entitlements` after subscription activation or plan
change.

Seed requirements for `search_saas`:

- `free`: `basic_search` enabled
- `pro`: `basic_search` enabled, `ai_search_per_use` limited to 100 uses,
  `ai_search_tokens` limited to 100000 tokens

## ADR-006: security definer functions are hardened

Every `security definer` function must:

- set an explicit `search_path` ending with `pg_temp`
- revoke execute from `public`
- grant execute only to `platform_backend_role`

The initial migrations create `platform_backend_role` if it does not already
exist and grant it only the schema/function privileges required by Platform
Core.

## ADR-007: User entitlements narrow organization entitlements

When both organization-level and user-level limits exist, the effective limit is
the smaller value.

`EntitlementService.getEffectiveLimit(...)` must return:

- `org_limit` when no user limit exists
- `min(org_limit, user_limit)` when a user limit exists

Usage functions that support `organization_and_user` counters must enforce both
the organization counter and the user counter.

## ADR-008: Test PostgreSQL includes a minimal auth schema

Testcontainers uses plain PostgreSQL, not a full Supabase stack. Integration
tests that need foreign keys to Supabase Auth must create a minimal test-only
`auth.users` table before platform migrations run.

This test schema is not a production migration and must not replace Supabase
Auth in deployed environments.

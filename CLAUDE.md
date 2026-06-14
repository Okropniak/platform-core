# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

```bash
# Build
./mvnw clean package

# Run all tests
./mvnw test

# Run integration tests (spins up Testcontainers PostgreSQL)
./mvnw verify

# Run a single test class
./mvnw test -Dtest=ClassName

# Run the application
./mvnw spring-boot:run

# Compile only (quick check)
./mvnw clean compile
```

On Windows use `mvnw.cmd` instead of `./mvnw`.

## Architecture Overview

**platform-core** is a SaaS Platform Core — a shared infrastructure backend that serves multiple independent SaaS products. It is a **modular monolith** (Spring Modulith), single deployable JAR, with strict module-boundary enforcement.

### Planned Module Structure

```
platform-core
├── identity      — User profiles, Supabase Auth integration
├── tenants       — Organizations, memberships, invitations
├── products      — Product registry, access control
├── billing       — Plans, subscriptions, Stripe integration
├── entitlements  — Features, metrics, usage limits
├── usage         — Counters, reservations, events
├── audit         — Immutable audit log
└── admin         — Internal admin APIs
```

Each module will live under `pl.zydron.platform.platformcore.<module>`. Spring Modulith enforces that modules only communicate via their published API (no cross-package imports into internals).

### Multi-Tenancy

Every tenant-owned row carries `organization_id`. Row-Level Security (RLS) is enforced at the PostgreSQL (Supabase) layer. The platform is product-agnostic — domain logic stays in product services; this core only knows `product_code`, `feature_code`, `metric_code`.

### Key Patterns

**Usage Metering (Reserve → Execute → Finalize):** Token-heavy operations (e.g., LLM calls) reserve capacity upfront, then finalize actual consumption. Failed operations release the reservation.

**Idempotency:** All billable/state-changing operations require caller-supplied idempotency keys.

**Access hierarchy:** `Payment → Subscription → Entitlements → Product Access`

**Organization-level vs user-level entitlements:** Both exist; enforcement rules resolve which limit applies.

### Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.0 |
| Modulith | Spring Modulith 2.1.0-RC1 |
| Auth | Spring Security + OAuth2 Resource Server (Supabase JWT) |
| Database | PostgreSQL via Supabase |
| Migrations | Flyway (`src/main/resources/db/migration/`) |
| ORM | Spring Data JPA + Hibernate |
| Testing | JUnit 5, Testcontainers (PostgreSQL), Spring Boot Test |
| Code gen | Lombok |

### Configuration

`src/main/resources/application.yaml` — currently minimal (app name only). Database and security config will be added here as modules are implemented.

### Test Infrastructure

`TestcontainersConfiguration.java` provides a real PostgreSQL container via `@ServiceConnection` — no mocking of the database. `TestPlatformCoreApplication.java` bootstraps the full app with Testcontainers for integration test runs.

### Design Documentation

Full HLD and LLD are in `docs/`:
- `docs/SaaS_Platform_Core_HLD.md` — vision, security model, deployment topology
- `docs/SaaS_Platform_Core_LLD_reviewed.md` — SQL schemas, API contracts, module contracts, MVP sprint order

Consult the LLD before implementing any module — it specifies the exact DB schemas, Flyway migration ordering, and inter-module event contracts.

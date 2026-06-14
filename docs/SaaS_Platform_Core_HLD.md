# SaaS Platform Core Architecture
## High Level Design (HLD)

Version: 1.0

---

# 1. Executive Summary

The goal is to build a reusable SaaS Platform Core that enables a small team to launch and operate multiple SaaS products while minimizing operational costs.

Key principles:

- One Supabase project
- Shared authentication
- Shared tenant management
- Shared billing
- Shared entitlements
- Shared usage metering
- One PostgreSQL schema per SaaS
- Separate frontend and backend per SaaS
- Ability to split products later if required

---

# 2. Architectural Drivers

## Business Drivers

- Launch multiple SaaS products quickly
- Reuse infrastructure
- Minimize operational cost
- Maintain legal separation between products
- Support subscription-based monetization

## Technical Drivers

- Multi-tenancy
- Scalability
- Security
- Product independence
- Future extraction capability

---

# 3. High-Level Architecture

```text
                    Supabase Auth
                           │
                           ▼
                 Platform Core Services (modular monolith)
                           │
 ┌──────────────┬──────────┼──────────┬──────────────┐
 ▼              ▼          ▼          ▼              ▼
Tenants     Billing   Entitlements  Usage      Product Registry
                           │
                           ▼
     ┌────────────┬────────────┬────────────┐
     ▼            ▼            ▼
 Search SaaS   Grant SaaS   Future SaaS
```

---

# 4. Supabase Structure

Single Supabase Project:

```text
saas-platform-prod
```

Schemas:

```text
platform
billing
entitlement
usage
audit

search_saas
grant_saas
architecture_saas
```

---

# 5. Identity and Authentication

Authentication is centralized.

Supabase Auth acts as:

- Identity Provider
- Session Provider
- Password Management
- MFA Provider

All SaaS applications trust the same identity source.

User exists once.

Product registrations are stored separately.

---

# 6. Tenant Model

## Organizations

Every business customer operates within an organization.

```text
Organization
 ├── Users
 ├── Subscriptions
 ├── Entitlements
 └── Usage
```

## Roles

Supported roles:

- Owner
- Admin
- Member

---

# 7. Product Model

Products are registered centrally.

Examples:

```text
search_saas
grant_saas
architecture_saas
```

Each product owns:

- frontend
- backend
- database schema
- domain model

The platform owns:

- identity
- billing
- access
- metering

---

# 8. Product Registration

A user may have one account but be registered to zero or more products.

Example:

```text
User
 ├── Service1 SaaS
 └── Service2 SaaS
```

Not registered:

```text
Service3 SaaS
```

This supports legal separation and product-specific terms acceptance.

---

# 9. Database Design

## Platform Schema

Core tables:

```text
profiles
organizations
organization_members
products
product_registrations
product_access
```

## Billing Schema

```text
plans
subscriptions
```

## Entitlement Schema

```text
features
metrics
organization_entitlements
user_entitlements
```

## Usage Schema

```text
usage_counters
usage_reservations
usage_events
```

---

# 10. Billing Architecture

Providers:

- Stripe
- PayU
- Przelewy24
- Tpay

Provider never grants access directly.

Flow:

```text
Payment
   │
   ▼
Subscription
   │
   ▼
Entitlements
   │
   ▼
Product Access
```

---

# 11. Entitlement Architecture

Entitlements define what a tenant or user can use.

Example:

```text
basic_search
ai_search_per_use
ai_search_tokens
```

Plans generate entitlements.

Example:

```text
PRO

basic_search=true
ai_search_per_use=100
ai_search_tokens=100000
```

---

# 12. Usage Metering

Supported models:

## Per Use

```text
AI Search A
1 request = 1 use
```

## Token Based

```text
AI Search B
Consumption based on token count
```

## Future

- seats
- documents
- storage
- API calls

---

# 13. Usage Reservation Pattern

Used for LLM workloads.

```text
Estimate
  ↓
Reserve
  ↓
Execute
  ↓
Finalize
```

Example:

```text
Estimated: 2000 tokens
Actual:    1437 tokens
Release:    563 tokens
```

---

# 14. Security Model

## Tenant Isolation

Every tenant-owned row contains:

```text
organization_id
```

## Product Isolation

Every product has its own schema.

```text
Service1_saas.*
Service2_saas.*
Service3_saas.*
```

---

# 15. Row Level Security

RLS is enforced for all tenant-owned data.

Policy concept:

```text
User
  ↓
Organization Membership
  ↓
Allowed Rows
```

No tenant can access another tenant's data.

---

# 16. SaaS Backend Contract

Each SaaS backend can call:

```text
check_product_access()
get_entitlements()
consume_usage()
reserve_usage()
finalize_usage()
```

Platform remains domain-agnostic.

---

# 17. Search SaaS Example

Features:

```text
basic_search
ai_search_per_use
ai_search_tokens
```

Flow:

```text
User Request
      ↓
Check Access
      ↓
Check Entitlement
      ↓
Consume Usage
      ↓
Execute Search
```

---

# 18. Deployment Model

Initial deployment:

```text
At the early stage, the platform should use the simplest possible deployment model.  
```  
A recommended starting point is:  
  
```text  
VPS  
+ Docker  
+ Coolify / Dokploy  
+ Reverse Proxy
```

Services:

```text
platform-admin-frontend  
platform-api / platform-functions  
example-saas-frontend  
example-saas-backend  
another-saas-frontend  
another-saas-backend
```

---

# 19. Frontend Architecture

Recommended:

```text
saas1.example.com
saas2.example.com
saas3.example.com
```

Avoid:

```text
example.com/saas1
example.com/saas2
```

Subdomains provide cleaner separation.

---

# 20. Repository Structure

```text
platform-core

saas1-saas
saas2-saas
saas3-saas
```

Platform evolves independently.

---

# 21. Monitoring

Track:

- active users
- subscriptions
- entitlement violations
- quota consumption
- API latency

---

# 22. Migration Strategy

Phase 1:

```text
One Supabase Project
Many Schemas
```

Phase 2:

```text
Extract Product Schema
```

Phase 3:

```text
Dedicated Supabase Project
```

Only when justified.

---

# 23. Risks

## Shared Blast Radius

One database affects all products.

Mitigation:

- schema boundaries
- monitoring
- migration review

## Growing Complexity

Mitigation:

- strict platform contracts
- no product-specific logic in platform

---

# 24. Roadmap

## Sprint 1

- Auth
- Organizations
- RLS

## Sprint 2

- Product Registry
- Product Access

## Sprint 3

- Entitlements

## Sprint 4

- Usage Metering

## Sprint 5

- Billing

## Sprint 6

- First SaaS Integration

## Sprint 7

- Admin Portal

---

# 25. Final Recommendation

For a small team building multiple SaaS products:

- One Supabase Project
- Shared Auth
- Shared Platform Core
- One Schema per SaaS
- Separate Frontends
- Separate Backends
- Central Billing
- Central Entitlements
- Central Usage Metering

This provides the lowest operational cost while preserving clear architectural boundaries and future scalability.

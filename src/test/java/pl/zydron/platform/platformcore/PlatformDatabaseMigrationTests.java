package pl.zydron.platform.platformcore;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import pl.zydron.platform.platformcore.audit.AuditService;
import pl.zydron.platform.platformcore.billing.BillingService;
import pl.zydron.platform.platformcore.entitlements.EntitlementService;
import pl.zydron.platform.platformcore.products.ProductService;
import pl.zydron.platform.platformcore.tenants.TenantService;
import pl.zydron.platform.platformcore.usage.UsageService;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.modulith.events.completion-mode=archive")
class PlatformDatabaseMigrationTests {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntitlementService entitlementService;

    @Autowired
    AuditService auditService;

    @Autowired
    BillingService billingService;

    @Autowired
    TenantService tenantService;

    @Autowired
    ProductService productService;

    @Autowired
    UsageService usageService;

    @Autowired
    EventPublicationRegistry eventPublicationRegistry;

    @Test
    void modulithPublicationRegistryUsesFlywayManagedPlatformTables() {
        var event = new ModulithTestEvent(UUID.randomUUID().toString());
        var target = PublicationTargetIdentifier.of("migration-test-listener");

        var publications = eventPublicationRegistry.store(event, java.util.stream.Stream.of(target));

        assertThat(publications).hasSize(1);
        Integer activeCount = jdbcTemplate.queryForObject(
                "select count(*) from platform.event_publication where listener_id = ?",
                Integer.class,
                target.getValue()
        );
        assertThat(activeCount).isEqualTo(1);

        eventPublicationRegistry.markCompleted(event, target);

        Integer remainingCount = jdbcTemplate.queryForObject(
                "select count(*) from platform.event_publication where listener_id = ?",
                Integer.class,
                target.getValue()
        );
        Integer archiveCount = jdbcTemplate.queryForObject(
                "select count(*) from platform.event_publication_archive where listener_id = ?",
                Integer.class,
                target.getValue()
        );
        Boolean backendPrivileges = jdbcTemplate.queryForObject(
                """
                select has_table_privilege(
                           'platform_backend_role',
                           'platform.event_publication',
                           'select,insert,update,delete'
                       )
                   and has_table_privilege(
                           'platform_backend_role',
                           'platform.event_publication_archive',
                           'select,insert,update,delete'
                       )
                """,
                Boolean.class
        );
        assertThat(remainingCount).isZero();
        assertThat(archiveCount).isEqualTo(1);
        assertThat(backendPrivileges).isTrue();
    }

    @Test
    void planEntitlementsRequireExistingBillingPlan() {
        Boolean constraintExists = jdbcTemplate.queryForObject(
                """
                select exists (
                    select 1
                    from pg_constraint
                    where conname = 'plan_entitlements_plan_fk'
                      and conrelid = 'billing.plan_entitlements'::regclass
                )
                """,
                Boolean.class
        );

        assertThat(constraintExists).isTrue();
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                insert into billing.plan_entitlements (
                    product_code,
                    plan_code,
                    feature_code,
                    enabled,
                    period
                )
                values ('search_saas', 'missing-plan', 'basic_search', true, 'monthly')
                """
        )).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("plan_entitlements_plan_fk");
    }

    @Test
    void grantsAuthenticatedExecuteOnRlsHelper() {
        Boolean allowed = jdbcTemplate.queryForObject(
                "select has_function_privilege('authenticated', 'platform.is_org_member(uuid, uuid)', 'execute')",
                Boolean.class
        );

        assertThat(allowed).isTrue();
    }

    @Test
    void grantsBackendDmlOnPlatformTables() {
        Boolean allowed = jdbcTemplate.queryForObject(
                "select has_table_privilege('platform_backend_role', 'platform.profiles', 'select,insert,update,delete')",
                Boolean.class
        );

        assertThat(allowed).isTrue();
    }

    @Test
    void seedsInitialProducts() {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from platform.products
                where code in ('search_saas', 'grant_saas', 'architecture_saas')
                  and status = 'active'
                """,
                Integer.class
        );

        assertThat(count).isEqualTo(3);
    }

    @Test
    void grantsProductFunctionPrivileges() {
        Boolean backendAllowed = jdbcTemplate.queryForObject(
                "select has_function_privilege('platform_backend_role', 'platform.check_product_access(uuid, uuid, text)', 'execute')",
                Boolean.class
        );
        Boolean authenticatedAllowed = jdbcTemplate.queryForObject(
                "select has_function_privilege('authenticated', 'platform.has_product_access(uuid, uuid, text)', 'execute')",
                Boolean.class
        );

        assertThat(backendAllowed).isTrue();
        assertThat(authenticatedAllowed).isTrue();
    }

    @Test
    void productAccessFunctionDoesNotExposeRoleForDisabledAccess() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();

        jdbcTemplate.update("insert into auth.users (id) values (?)", userId);
        jdbcTemplate.update(
                "insert into platform.organizations (id, name, type, created_by) values (?, 'Acme', 'company', ?)",
                organizationId,
                userId
        );
        jdbcTemplate.update(
                "insert into platform.organization_members (organization_id, user_id, role, status) values (?, ?, 'owner', 'active')",
                organizationId,
                userId
        );
        jdbcTemplate.update(
                """
                insert into platform.product_access (organization_id, user_id, product_code, role, enabled)
                values (?, ?, 'search_saas', 'user', false)
                """,
                organizationId,
                userId
        );

        Boolean allowed = jdbcTemplate.queryForObject(
                "select (platform.check_product_access(?, ?, 'search_saas')->>'allowed')::boolean",
                Boolean.class,
                organizationId,
                userId
        );
        String role = jdbcTemplate.queryForObject(
                "select platform.check_product_access(?, ?, 'search_saas')->>'role'",
                String.class,
                organizationId,
                userId
        );

        assertThat(allowed).isFalse();
        assertThat(role).isNull();
    }

    @Test
    void seedsSearchSaasEntitlementCatalog() {
        Integer features = jdbcTemplate.queryForObject(
                "select count(*) from entitlement.features where product_code = 'search_saas' and active",
                Integer.class
        );
        Integer metrics = jdbcTemplate.queryForObject(
                "select count(*) from entitlement.metrics where product_code = 'search_saas'",
                Integer.class
        );

        assertThat(features).isEqualTo(3);
        assertThat(metrics).isEqualTo(2);
    }

    @Test
    void grantsBackendExecuteOnGetEntitlements() {
        Boolean allowed = jdbcTemplate.queryForObject(
                "select has_function_privilege('platform_backend_role', 'entitlement.get_entitlements(uuid, text)', 'execute')",
                Boolean.class
        );

        assertThat(allowed).isTrue();
    }

    @Test
    void getEntitlementsReturnsCurrentOrganizationEntitlements() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();

        jdbcTemplate.update("insert into auth.users (id) values (?)", userId);
        jdbcTemplate.update(
                "insert into platform.organizations (id, name, type, created_by) values (?, 'Entitlement Org', 'company', ?)",
                organizationId,
                userId
        );
        jdbcTemplate.update(
                "insert into platform.organization_members (organization_id, user_id, role, status) values (?, ?, 'owner', 'active')",
                organizationId,
                userId
        );
        jdbcTemplate.update(
                """
                insert into entitlement.organization_entitlements (
                    organization_id,
                    product_code,
                    feature_code,
                    metric_code,
                    limit_value,
                    period,
                    source
                )
                values (?, 'search_saas', 'ai_search_per_use', 'ai_search_usage', 100, 'monthly', 'plan')
                """,
                organizationId
        );

        String limit = jdbcTemplate.queryForObject(
                "select entitlement.get_entitlements(?, 'search_saas')->'ai_search_per_use'->>'limitValue'",
                String.class,
                organizationId
        );

        assertThat(limit).isEqualTo("100");
    }

    @Test
    void syncEntitlementsFromPlanUpsertsSearchSaasTemplate() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();

        jdbcTemplate.update("insert into auth.users (id) values (?)", userId);
        jdbcTemplate.update(
                "insert into platform.organizations (id, name, type, created_by) values (?, 'Plan Sync Org', 'company', ?)",
                organizationId,
                userId
        );

        entitlementService.syncEntitlementsFromPlan(organizationId, "search_saas", "pro");
        entitlementService.syncEntitlementsFromPlan(organizationId, "search_saas", "pro");

        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from entitlement.organization_entitlements
                where organization_id = ?
                  and product_code = 'search_saas'
                  and source = 'plan'
                """,
                Integer.class,
                organizationId
        );
        String tokenLimit = jdbcTemplate.queryForObject(
                """
                select limit_value::text
                from entitlement.organization_entitlements
                where organization_id = ?
                  and product_code = 'search_saas'
                  and feature_code = 'ai_search_tokens'
                  and metric_code = 'ai_search_tokens'
                """,
                String.class,
                organizationId
        );

        assertThat(count).isEqualTo(3);
        assertThat(tokenLimit).isEqualTo("100000");
    }

    @Test
    void syncEntitlementsFromPlanSoftDisablesEntriesRemovedByDowngrade() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();

        jdbcTemplate.update("insert into auth.users (id) values (?)", userId);
        jdbcTemplate.update(
                "insert into platform.organizations (id, name, type, created_by) values (?, 'Downgrade Org', 'company', ?)",
                organizationId,
                userId
        );

        entitlementService.syncEntitlementsFromPlan(organizationId, "search_saas", "pro");
        entitlementService.syncEntitlementsFromPlan(organizationId, "search_saas", "free");

        Integer enabledCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from entitlement.organization_entitlements
                where organization_id = ?
                  and product_code = 'search_saas'
                  and source = 'plan'
                  and enabled
                """,
                Integer.class,
                organizationId
        );
        Boolean tokensEnabled = jdbcTemplate.queryForObject(
                """
                select enabled
                from entitlement.organization_entitlements
                where organization_id = ?
                  and product_code = 'search_saas'
                  and feature_code = 'ai_search_tokens'
                  and metric_code = 'ai_search_tokens'
                """,
                Boolean.class,
                organizationId
        );

        assertThat(enabledCount).isEqualTo(2);
        assertThat(tokensEnabled).isFalse();
    }

    @Test
    void grantsBackendExecuteOnUsageFunctions() {
        Boolean consumeAllowed = jdbcTemplate.queryForObject(
                "select has_function_privilege('platform_backend_role', 'usage.consume_usage(uuid, uuid, text, text, numeric, text)', 'execute')",
                Boolean.class
        );
        Boolean reserveAllowed = jdbcTemplate.queryForObject(
                "select has_function_privilege('platform_backend_role', 'usage.reserve_usage(uuid, uuid, text, text, numeric, text)', 'execute')",
                Boolean.class
        );
        Boolean finalizeAllowed = jdbcTemplate.queryForObject(
                "select has_function_privilege('platform_backend_role', 'usage.finalize_usage(uuid, uuid, numeric)', 'execute')",
                Boolean.class
        );

        assertThat(consumeAllowed).isTrue();
        assertThat(reserveAllowed).isTrue();
        assertThat(finalizeAllowed).isTrue();
    }

    @Test
    void auditSchemaSupportsJsonMetadataAndExpectedIndexes() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        jdbcTemplate.update("insert into auth.users (id) values (?)", userId);
        jdbcTemplate.update(
                "insert into platform.organizations (id, name, type, created_by) values (?, 'Audit Org', 'company', ?)",
                organizationId,
                userId
        );

        auditService.record(
                organizationId,
                userId,
                "search_saas",
                "product_registered",
                "product_registration",
                "registration-1",
                Map.of("status", "active")
        );

        String status = awaitAuditStatus(organizationId);
        Boolean orgIndexExists = jdbcTemplate.queryForObject(
                "select to_regclass('audit.audit_events_organization_created_idx') is not null",
                Boolean.class
        );
        Boolean productIndexExists = jdbcTemplate.queryForObject(
                "select to_regclass('audit.audit_events_product_type_created_idx') is not null",
                Boolean.class
        );
        Boolean appendOnly = jdbcTemplate.queryForObject(
                """
                select has_table_privilege('platform_backend_role', 'audit.audit_events', 'select,insert')
                   and not has_table_privilege('platform_backend_role', 'audit.audit_events', 'update,delete')
                """,
                Boolean.class
        );

        assertThat(status).isEqualTo("active");
        assertThat(orgIndexExists).isTrue();
        assertThat(productIndexExists).isTrue();
        assertThat(appendOnly).isTrue();
    }

    @Test
    void seedsSearchSaasBillingPlansAndProtectsSubscriptionReadsWithRls() {
        Integer plans = jdbcTemplate.queryForObject(
                """
                select count(*)
                from billing.plans
                where product_code = 'search_saas'
                  and plan_code in ('free', 'pro')
                  and active
                """,
                Integer.class
        );
        Boolean subscriptionsRls = jdbcTemplate.queryForObject(
                """
                select relrowsecurity
                from pg_class
                where oid = 'billing.subscriptions'::regclass
                """,
                Boolean.class
        );
        Boolean authenticatedCanReadSubscriptions = jdbcTemplate.queryForObject(
                "select has_table_privilege('authenticated', 'billing.subscriptions', 'select')",
                Boolean.class
        );

        assertThat(plans).isEqualTo(2);
        assertThat(subscriptionsRls).isTrue();
        assertThat(authenticatedCanReadSubscriptions).isTrue();
    }

    @Test
    void manualSubscriptionSyncsPlanEntitlementsAndCancellationDowngradesToFree() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        jdbcTemplate.update("insert into auth.users (id) values (?)", userId);
        jdbcTemplate.update(
                "insert into platform.organizations (id, name, type, created_by) values (?, 'Billing Org', 'company', ?)",
                organizationId,
                userId
        );
        jdbcTemplate.update(
                "insert into platform.organization_members (organization_id, user_id, role, status) values (?, ?, 'owner', 'active')",
                organizationId,
                userId
        );

        billingService.createManualSubscription(organizationId, userId, "search_saas", "pro");

        String proTokenLimit = jdbcTemplate.queryForObject(
                """
                select limit_value::text
                from entitlement.organization_entitlements
                where organization_id = ?
                  and product_code = 'search_saas'
                  and feature_code = 'ai_search_tokens'
                  and metric_code = 'ai_search_tokens'
                  and enabled
                """,
                String.class,
                organizationId
        );

        billingService.cancelSubscription(organizationId, userId, "search_saas");

        Integer disabledTokenEntitlements = jdbcTemplate.queryForObject(
                """
                select count(*)
                from entitlement.organization_entitlements
                where organization_id = ?
                  and product_code = 'search_saas'
                  and feature_code = 'ai_search_tokens'
                  and metric_code = 'ai_search_tokens'
                  and enabled = false
                """,
                Integer.class,
                organizationId
        );
        String subscriptionStatus = jdbcTemplate.queryForObject(
                """
                select status
                from billing.subscriptions
                where organization_id = ?
                  and product_code = 'search_saas'
                """,
                String.class,
                organizationId
        );

        assertThat(proTokenLimit).isEqualTo("100000");
        assertThat(disabledTokenEntitlements).isEqualTo(1);
        assertThat(subscriptionStatus).isEqualTo("cancelled");
    }

    @Test
    void searchSaasSchemaHasRlsAndExpectedPrivileges() {
        Boolean tableExists = jdbcTemplate.queryForObject(
                "select to_regclass('search_saas.search_queries') is not null",
                Boolean.class
        );
        Boolean rlsEnabled = jdbcTemplate.queryForObject(
                """
                select relrowsecurity
                from pg_class
                where oid = 'search_saas.search_queries'::regclass
                """,
                Boolean.class
        );
        Boolean authenticatedCanInsert = jdbcTemplate.queryForObject(
                "select has_table_privilege('authenticated', 'search_saas.search_queries', 'select,insert')",
                Boolean.class
        );
        Boolean backendCanWrite = jdbcTemplate.queryForObject(
                """
                select has_table_privilege('search_saas_backend_role', 'search_saas.search_queries', 'select,insert,update,delete')
                """,
                Boolean.class
        );

        assertThat(tableExists).isTrue();
        assertThat(rlsEnabled).isTrue();
        assertThat(authenticatedCanInsert).isTrue();
        assertThat(backendCanWrite).isTrue();
    }

    @Test
    void searchSaasContractWorksEndToEnd() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("insert into auth.users (id) values (?)", userId);

        var organization = tenantService.createOrganization(
                userId,
                "Search Contract Owner",
                "Search SaaS Contract Org",
                "company"
        );
        UUID organizationId = organization.getId();

        productService.registerUserToProduct(
                organizationId,
                userId,
                "search_saas",
                "2026-06",
                "2026-06"
        );
        productService.grantAccess(organizationId, userId, userId, "search_saas", "user");
        billingService.createManualSubscription(organizationId, userId, "search_saas", "pro");

        var entitlements = entitlementService.getEntitlements(organizationId, userId, "search_saas");
        assertThat(entitlements.entitlements()).containsKeys("basic_search", "ai_search_per_use", "ai_search_tokens");

        insertSearchQueryAsAuthenticatedUser(organizationId, userId, "basic contract query", "basic_search");
        Integer visibleQueries = countSearchQueriesAsAuthenticatedUser(organizationId, userId);
        assertThat(visibleQueries).isEqualTo(1);

        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update("insert into auth.users (id) values (?)", otherUserId);
        tenantService.createOrganization(
                otherUserId,
                "Other Contract Owner",
                "Other Search SaaS Org",
                "company"
        );
        Integer otherUserVisibleQueries = countSearchQueriesAsAuthenticatedUser(organizationId, otherUserId);
        assertThat(otherUserVisibleQueries).isZero();

        UUID memberWithoutAccessId = UUID.randomUUID();
        jdbcTemplate.update("insert into auth.users (id) values (?)", memberWithoutAccessId);
        tenantService.addMember(organizationId, userId, memberWithoutAccessId, "member");
        assertThatThrownBy(() -> insertSearchQueryAsAuthenticatedUser(
                organizationId,
                memberWithoutAccessId,
                "blocked query",
                "basic_search"
        )).isInstanceOf(DataAccessException.class);
        Integer memberWithoutAccessVisibleQueries = countSearchQueriesAsAuthenticatedUser(organizationId, memberWithoutAccessId);
        assertThat(memberWithoutAccessVisibleQueries).isZero();

        var firstUsage = usageService.consumeUsage(
                organizationId,
                userId,
                "search_saas",
                "ai_search_usage",
                BigDecimal.valueOf(1000),
                "search_saas:consume:e2e-1"
        );
        var overLimitUsage = usageService.consumeUsage(
                organizationId,
                userId,
                "search_saas",
                "ai_search_usage",
                BigDecimal.ONE,
                "search_saas:consume:e2e-2"
        );

        assertThat(firstUsage.accepted()).isTrue();
        assertThat(overLimitUsage.accepted()).isFalse();
        assertThat(overLimitUsage.reason()).isEqualTo("limit_exceeded");

        var reservation = usageService.reserveUsage(
                organizationId,
                userId,
                "search_saas",
                "ai_search_tokens",
                BigDecimal.valueOf(7),
                "search_saas:reserve:e2e-1"
        );
        var finalization = usageService.finalizeUsage(reservation.reservationId(), userId, BigDecimal.valueOf(5));

        assertThat(reservation.accepted()).isTrue();
        assertThat(finalization.finalized()).isTrue();

        String tokenUsed = jdbcTemplate.queryForObject(
                """
                select used_value::text
                from usage.usage_counters
                where organization_id = ?
                  and product_code = 'search_saas'
                  and metric_code = 'ai_search_tokens'
                  and counter_scope = 'organization'
                """,
                String.class,
                organizationId
        );
        String tokenReserved = jdbcTemplate.queryForObject(
                """
                select reserved_value::text
                from usage.usage_counters
                where organization_id = ?
                  and product_code = 'search_saas'
                  and metric_code = 'ai_search_tokens'
                  and counter_scope = 'organization'
                """,
                String.class,
                organizationId
        );
        assertThat(tokenUsed).isEqualTo("5");
        assertThat(tokenReserved).isEqualTo("0");

        Integer limitAuditEvents = awaitAuditEventCount(organizationId, "usage_limit_exceeded");
        assertThat(limitAuditEvents).isGreaterThanOrEqualTo(1);
    }

    @Test
    void consumeUsageIsIdempotentAndEnforcesLimit() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = createUsageFixture(userId, "Consume Org", "ai_search_usage", "ai_search_per_use", "3");

        Boolean firstAccepted = jdbcTemplate.queryForObject(
                "select (usage.consume_usage(?, ?, 'search_saas', 'ai_search_usage', 2, 'search_saas:consume:test-1')->>'accepted')::boolean",
                Boolean.class,
                organizationId,
                userId
        );
        Boolean retryAccepted = jdbcTemplate.queryForObject(
                "select (usage.consume_usage(?, ?, 'search_saas', 'ai_search_usage', 2, 'search_saas:consume:test-1')->>'accepted')::boolean",
                Boolean.class,
                organizationId,
                userId
        );
        String secondReason = jdbcTemplate.queryForObject(
                "select usage.consume_usage(?, ?, 'search_saas', 'ai_search_usage', 2, 'search_saas:consume:test-2')->>'reason'",
                String.class,
                organizationId,
                userId
        );
        String used = jdbcTemplate.queryForObject(
                """
                select used_value::text
                from usage.usage_counters
                where organization_id = ?
                  and product_code = 'search_saas'
                  and metric_code = 'ai_search_usage'
                  and counter_scope = 'organization'
                """,
                String.class,
                organizationId
        );
        Integer eventCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from usage.usage_events
                where organization_id = ?
                  and product_code = 'search_saas'
                  and metric_code = 'ai_search_usage'
                  and event_type = 'consume'
                """,
                Integer.class,
                organizationId
        );

        assertThat(firstAccepted).isTrue();
        assertThat(retryAccepted).isTrue();
        assertThat(secondReason).isEqualTo("limit_exceeded");
        assertThat(used).isEqualTo("2");
        assertThat(eventCount).isEqualTo(2);
    }

    @Test
    void consumeUsageEnforcesUserEntitlementAndUpdatesUserCounter() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = createUsageFixture(userId, "User Consume Org", "ai_search_usage", "ai_search_per_use", "100");
        addUserEntitlement(organizationId, userId, "ai_search_usage", "ai_search_per_use", "2");

        Boolean firstAccepted = jdbcTemplate.queryForObject(
                "select (usage.consume_usage(?, ?, 'search_saas', 'ai_search_usage', 2, 'search_saas:consume:user-1')->>'accepted')::boolean",
                Boolean.class,
                organizationId,
                userId
        );
        String secondReason = jdbcTemplate.queryForObject(
                "select usage.consume_usage(?, ?, 'search_saas', 'ai_search_usage', 1, 'search_saas:consume:user-2')->>'reason'",
                String.class,
                organizationId,
                userId
        );
        String userUsed = jdbcTemplate.queryForObject(
                """
                select used_value::text
                from usage.usage_counters
                where organization_id = ?
                  and user_id = ?
                  and product_code = 'search_saas'
                  and metric_code = 'ai_search_usage'
                  and counter_scope = 'user'
                """,
                String.class,
                organizationId,
                userId
        );

        assertThat(firstAccepted).isTrue();
        assertThat(secondReason).isEqualTo("limit_exceeded");
        assertThat(userUsed).isEqualTo("2");
    }

    @Test
    void reserveAndFinalizeUsageMovesReservedToUsed() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = createUsageFixture(userId, "Reserve Org", "ai_search_tokens", "ai_search_tokens", "10");

        String reservationId = jdbcTemplate.queryForObject(
                "select usage.reserve_usage(?, ?, 'search_saas', 'ai_search_tokens', 7, 'search_saas:reserve:test-1')->>'reservationId'",
                String.class,
                organizationId,
                userId
        );
        String reservationOwner = jdbcTemplate.queryForObject(
                "select user_id::text from usage.usage_reservations where id = ?::uuid and counter_scope = 'organization'",
                String.class,
                reservationId
        );
        Boolean finalized = jdbcTemplate.queryForObject(
                "select (usage.finalize_usage(?::uuid, ?, 5)->>'finalized')::boolean",
                Boolean.class,
                reservationId,
                userId
        );
        String used = jdbcTemplate.queryForObject(
                """
                select used_value::text
                from usage.usage_counters
                where organization_id = ?
                  and product_code = 'search_saas'
                  and metric_code = 'ai_search_tokens'
                  and counter_scope = 'organization'
                """,
                String.class,
                organizationId
        );
        String reserved = jdbcTemplate.queryForObject(
                """
                select reserved_value::text
                from usage.usage_counters
                where organization_id = ?
                  and product_code = 'search_saas'
                  and metric_code = 'ai_search_tokens'
                  and counter_scope = 'organization'
                """,
                String.class,
                organizationId
        );

        assertThat(reservationOwner).isEqualTo(userId.toString());
        assertThat(finalized).isTrue();
        assertThat(used).isEqualTo("5");
        assertThat(reserved).isEqualTo("0");
    }

    @Test
    void reserveAndFinalizeUsageMovesUserCounterWhenUserEntitlementExists() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = createUsageFixture(userId, "User Reserve Org", "ai_search_tokens", "ai_search_tokens", "100");
        addUserEntitlement(organizationId, userId, "ai_search_tokens", "ai_search_tokens", "10");

        String reservationId = jdbcTemplate.queryForObject(
                "select usage.reserve_usage(?, ?, 'search_saas', 'ai_search_tokens', 7, 'search_saas:reserve:user-1')->>'reservationId'",
                String.class,
                organizationId,
                userId
        );
        String scope = jdbcTemplate.queryForObject(
                "select counter_scope from usage.usage_reservations where id = ?::uuid",
                String.class,
                reservationId
        );
        Boolean finalized = jdbcTemplate.queryForObject(
                "select (usage.finalize_usage(?::uuid, ?, 5)->>'finalized')::boolean",
                Boolean.class,
                reservationId,
                userId
        );
        String userUsed = jdbcTemplate.queryForObject(
                """
                select used_value::text
                from usage.usage_counters
                where organization_id = ?
                  and user_id = ?
                  and product_code = 'search_saas'
                  and metric_code = 'ai_search_tokens'
                  and counter_scope = 'user'
                """,
                String.class,
                organizationId,
                userId
        );
        String userReserved = jdbcTemplate.queryForObject(
                """
                select reserved_value::text
                from usage.usage_counters
                where organization_id = ?
                  and user_id = ?
                  and product_code = 'search_saas'
                  and metric_code = 'ai_search_tokens'
                  and counter_scope = 'user'
                """,
                String.class,
                organizationId,
                userId
        );

        assertThat(scope).isEqualTo("organization_and_user");
        assertThat(finalized).isTrue();
        assertThat(userUsed).isEqualTo("5");
        assertThat(userReserved).isEqualTo("0");
    }

    @Test
    void finalizeUsageRejectsActualAmountGreaterThanReservation() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = createUsageFixture(userId, "Overspend Org", "ai_search_tokens", "ai_search_tokens", "10");

        String reservationId = jdbcTemplate.queryForObject(
                "select usage.reserve_usage(?, ?, 'search_saas', 'ai_search_tokens', 7, 'search_saas:reserve:overspend')->>'reservationId'",
                String.class,
                organizationId,
                userId
        );
        String reason = jdbcTemplate.queryForObject(
                "select usage.finalize_usage(?::uuid, ?, 8)->>'reason'",
                String.class,
                reservationId,
                userId
        );

        assertThat(reason).isEqualTo("actual_exceeds_reservation");
    }

    @Test
    void finalizeUsageReportsMissingCounterAsReservationInconsistency() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = createUsageFixture(userId, "Missing Counter Org", "ai_search_tokens", "ai_search_tokens", "10");
        addUserEntitlement(organizationId, userId, "ai_search_tokens", "ai_search_tokens", "10");

        String reservationId = jdbcTemplate.queryForObject(
                "select usage.reserve_usage(?, ?, 'search_saas', 'ai_search_tokens', 7, 'search_saas:reserve:missing-counter')->>'reservationId'",
                String.class,
                organizationId,
                userId
        );
        jdbcTemplate.update(
                """
                delete from usage.usage_counters
                where organization_id = ?
                  and user_id = ?
                  and product_code = 'search_saas'
                  and metric_code = 'ai_search_tokens'
                  and counter_scope = 'user'
                """,
                organizationId,
                userId
        );

        String reason = jdbcTemplate.queryForObject(
                "select usage.finalize_usage(?::uuid, ?, 5)->>'reason'",
                String.class,
                reservationId,
                userId
        );

        assertThat(reason).isEqualTo("reservation_counter_missing");
    }

    @Test
    void testAuthUidFailsWhenSubjectIsNotConfigured() {
        assertThatThrownBy(() -> jdbcTemplate.queryForObject("select auth.uid()", String.class))
                .hasMessageContaining("test auth.uid() called without request.jwt.claim.sub");
    }

    private UUID createUsageFixture(
            UUID userId,
            String organizationName,
            String metricCode,
            String featureCode,
            String limitValue
    ) {
        UUID organizationId = UUID.randomUUID();
        jdbcTemplate.update("insert into auth.users (id) values (?)", userId);
        jdbcTemplate.update(
                "insert into platform.organizations (id, name, type, created_by) values (?, ?, 'company', ?)",
                organizationId,
                organizationName,
                userId
        );
        jdbcTemplate.update(
                "insert into platform.organization_members (organization_id, user_id, role, status) values (?, ?, 'owner', 'active')",
                organizationId,
                userId
        );
        jdbcTemplate.update(
                """
                insert into entitlement.organization_entitlements (
                    organization_id,
                    product_code,
                    feature_code,
                    metric_code,
                    enabled,
                    limit_value,
                    period,
                    source
                )
                values (?, 'search_saas', ?, ?, true, ?::numeric, 'monthly', 'manual')
                """,
                organizationId,
                featureCode,
                metricCode,
                limitValue
        );
        return organizationId;
    }

    private void addUserEntitlement(
            UUID organizationId,
            UUID userId,
            String metricCode,
            String featureCode,
            String limitValue
    ) {
        jdbcTemplate.update(
                """
                insert into entitlement.user_entitlements (
                    organization_id,
                    user_id,
                    product_code,
                    feature_code,
                    metric_code,
                    enabled,
                    limit_value,
                    period,
                    source
                )
                values (?, ?, 'search_saas', ?, ?, true, ?::numeric, 'monthly', 'manual')
                """,
                organizationId,
                userId,
                featureCode,
                metricCode,
                limitValue
        );
    }

    private void insertSearchQueryAsAuthenticatedUser(
            UUID organizationId,
            UUID userId,
            String query,
            String searchType
    ) {
        runAsAuthenticatedUser(userId, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    insert into search_saas.search_queries (organization_id, user_id, query, search_type)
                    values (?, ?, ?, ?)
                    """
            )) {
                statement.setObject(1, organizationId);
                statement.setObject(2, userId);
                statement.setString(3, query);
                statement.setString(4, searchType);
                statement.executeUpdate();
            }
            return null;
        });
    }

    private Integer countSearchQueriesAsAuthenticatedUser(UUID organizationId, UUID userId) {
        return runAsAuthenticatedUser(userId, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    select count(*)
                    from search_saas.search_queries
                    where organization_id = ?
                    """
            )) {
                statement.setObject(1, organizationId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getInt(1);
                }
            }
        });
    }

    private <T> T runAsAuthenticatedUser(UUID userId, AuthenticatedOperation<T> operation) {
        return jdbcTemplate.execute((Connection connection) -> {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("set local role authenticated");
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "select set_config('request.jwt.claim.sub', ?, true)"
                )) {
                    statement.setString(1, userId.toString());
                    statement.executeQuery();
                }
                T result = operation.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        });
    }

    @FunctionalInterface
    private interface AuthenticatedOperation<T> {
        T execute(Connection connection) throws SQLException;
    }

    private record ModulithTestEvent(String value) {
    }

    private String awaitAuditStatus(UUID organizationId) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            String status = jdbcTemplate.queryForObject(
                    """
                    select max(metadata->>'status')
                    from audit.audit_events
                    where organization_id = ?
                      and event_type = 'product_registered'
                    """,
                    String.class,
                    organizationId
            );
            if (status != null) {
                return status;
            }
            Thread.sleep(50);
        }
        return null;
    }

    private Integer awaitAuditEventCount(UUID organizationId, String eventType) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            Integer count = jdbcTemplate.queryForObject(
                    """
                    select count(*)
                    from audit.audit_events
                    where organization_id = ?
                      and event_type = ?
                    """,
                    Integer.class,
                    organizationId,
                    eventType
            );
            if (count != null && count > 0) {
                return count;
            }
            Thread.sleep(50);
        }
        return 0;
    }
}

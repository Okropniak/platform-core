package pl.zydron.platform.platformcore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.zydron.platform.platformcore.entitlements.EntitlementService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PlatformDatabaseMigrationTests {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntitlementService entitlementService;

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
}

package pl.zydron.platform.platformcore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PlatformDatabaseMigrationTests {

    @Autowired
    JdbcTemplate jdbcTemplate;

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
    void testAuthUidFailsWhenSubjectIsNotConfigured() {
        assertThatThrownBy(() -> jdbcTemplate.queryForObject("select auth.uid()", String.class))
                .hasMessageContaining("test auth.uid() called without request.jwt.claim.sub");
    }
}

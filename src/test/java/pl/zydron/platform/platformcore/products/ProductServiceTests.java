package pl.zydron.platform.platformcore.products;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import pl.zydron.platform.platformcore.common.BadRequestException;
import pl.zydron.platform.platformcore.tenants.TenantService;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceTests {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ProductRegistrationRepository productRegistrationRepository = mock(ProductRegistrationRepository.class);
    private final ProductAccessRepository productAccessRepository = mock(ProductAccessRepository.class);
    private final TenantService tenantService = mock(TenantService.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ProductService productService = new ProductService(
            productRepository,
            productRegistrationRepository,
            productAccessRepository,
            tenantService,
            jdbcTemplate,
            new ObjectMapper()
    );

    @Test
    void registerUserToProductReactivatesExistingRegistrationInsteadOfCreatingDuplicate() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var upserted = ProductRegistrationEntity.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .userId(userId)
                .productCode("search_saas")
                .acceptedTermsVersion("2026-06")
                .acceptedPrivacyVersion("2026-06")
                .status("active")
                .registeredAt(OffsetDateTime.now())
                .build();

        when(productRepository.findByCodeAndStatus("search_saas", "active"))
                .thenReturn(Optional.of(activeProduct()));
        when(jdbcTemplate.queryForObject(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<ProductRegistrationEntity>>any(),
                eq(organizationId),
                eq(userId),
                eq("search_saas"),
                eq("2026-06"),
                eq("2026-06")
        )).thenReturn(upserted);

        var registration = productService.registerUserToProduct(
                organizationId,
                userId,
                "search_saas",
                "2026-06",
                "2026-06"
        );

        assertThat(registration.getStatus()).isEqualTo("active");
        assertThat(registration.getAcceptedTermsVersion()).isEqualTo("2026-06");
        verify(productRegistrationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void grantAccessUpdatesExistingAccessExplicitly() {
        UUID organizationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var upserted = ProductAccessEntity.builder()
                .id(new ProductAccessId(organizationId, userId, "search_saas"))
                .role("admin")
                .enabled(true)
                .createdAt(OffsetDateTime.now())
                .build();

        when(productRepository.findByCodeAndStatus("search_saas", "active"))
                .thenReturn(Optional.of(activeProduct()));
        when(jdbcTemplate.queryForObject(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<ProductAccessEntity>>any(),
                eq(organizationId),
                eq(userId),
                eq("search_saas"),
                eq("admin")
        )).thenReturn(upserted);

        var access = productService.grantAccess(organizationId, adminId, userId, "search_saas", "admin");

        assertThat(access.getRole()).isEqualTo("admin");
        assertThat(access.isEnabled()).isTrue();
        verify(productAccessRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void checkAccessParsesDatabaseJsonResult() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(productRepository.findByCodeAndStatus("search_saas", "active"))
                .thenReturn(Optional.of(activeProduct()));
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(organizationId), eq(userId), eq("search_saas")))
                .thenReturn("{\"allowed\":true,\"role\":\"user\"}");

        var result = productService.checkAccess(organizationId, userId, userId, "search_saas");

        assertThat(result.allowed()).isTrue();
        assertThat(result.role()).isEqualTo("user");
    }

    @Test
    void checkAccessRequiresManagerWhenCheckingAnotherUser() {
        UUID organizationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID checkedUserId = UUID.randomUUID();

        when(productRepository.findByCodeAndStatus("search_saas", "active"))
                .thenReturn(Optional.of(activeProduct()));
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(organizationId), eq(checkedUserId), eq("search_saas")))
                .thenReturn("{\"allowed\":false,\"role\":null}");

        productService.checkAccess(organizationId, adminId, checkedUserId, "search_saas");

        verify(tenantService).requireManager(organizationId, adminId);
        verify(tenantService).requireActiveMember(organizationId, checkedUserId);
    }

    @Test
    void rejectsUnknownProductBeforeDatabaseWrite() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(productRepository.findByCodeAndStatus("missing", "active"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.registerUserToProduct(organizationId, userId, "missing", null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Product does not exist");

        verify(productRegistrationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private ProductEntity activeProduct() {
        return ProductEntity.builder()
                .id(UUID.randomUUID())
                .code("search_saas")
                .name("Search SaaS")
                .status("active")
                .createdAt(OffsetDateTime.now())
                .build();
    }
}

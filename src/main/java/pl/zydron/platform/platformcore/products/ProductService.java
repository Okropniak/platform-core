package pl.zydron.platform.platformcore.products;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.zydron.platform.platformcore.common.BadRequestException;
import pl.zydron.platform.platformcore.tenants.TenantService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductRegistrationRepository productRegistrationRepository;
    private final ProductAccessRepository productAccessRepository;
    private final TenantService tenantService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public ProductRegistrationEntity registerUserToProduct(
            UUID organizationId,
            UUID userId,
            String productCode,
            String termsVersion,
            String privacyVersion
    ) {
        tenantService.requireActiveMember(organizationId, userId);
        requireActiveProduct(productCode);

        return jdbcTemplate.queryForObject(
                """
                insert into platform.product_registrations (
                    organization_id,
                    user_id,
                    product_code,
                    accepted_terms_version,
                    accepted_privacy_version,
                    status
                )
                values (?, ?, ?, ?, ?, 'active')
                on conflict (organization_id, user_id, product_code) do update
                set accepted_terms_version = excluded.accepted_terms_version,
                    accepted_privacy_version = excluded.accepted_privacy_version,
                    status = 'active'
                returning id,
                          organization_id,
                          user_id,
                          product_code,
                          accepted_terms_version,
                          accepted_privacy_version,
                          status,
                          registered_at
                """,
                (rs, rowNum) -> ProductRegistrationEntity.builder()
                        .id(rs.getObject("id", UUID.class))
                        .organizationId(rs.getObject("organization_id", UUID.class))
                        .userId(rs.getObject("user_id", UUID.class))
                        .productCode(rs.getString("product_code"))
                        .acceptedTermsVersion(rs.getString("accepted_terms_version"))
                        .acceptedPrivacyVersion(rs.getString("accepted_privacy_version"))
                        .status(rs.getString("status"))
                        .registeredAt(rs.getObject("registered_at", OffsetDateTime.class))
                        .build(),
                organizationId,
                userId,
                productCode,
                termsVersion,
                privacyVersion
        );
    }

    @Transactional
    public ProductAccessEntity grantAccess(
            UUID organizationId,
            UUID requestingUserId,
            UUID targetUserId,
            String productCode,
            String role
    ) {
        tenantService.requireManager(organizationId, requestingUserId);
        tenantService.requireActiveMember(organizationId, targetUserId);
        requireActiveProduct(productCode);

        return jdbcTemplate.queryForObject(
                """
                insert into platform.product_access (
                    organization_id,
                    user_id,
                    product_code,
                    role,
                    enabled
                )
                values (?, ?, ?, ?, true)
                on conflict (organization_id, user_id, product_code) do update
                set role = excluded.role,
                    enabled = true
                returning organization_id,
                          user_id,
                          product_code,
                          role,
                          enabled,
                          created_at
                """,
                (rs, rowNum) -> ProductAccessEntity.builder()
                        .id(new ProductAccessId(
                                rs.getObject("organization_id", UUID.class),
                                rs.getObject("user_id", UUID.class),
                                rs.getString("product_code")
                        ))
                        .role(rs.getString("role"))
                        .enabled(rs.getBoolean("enabled"))
                        .createdAt(rs.getObject("created_at", OffsetDateTime.class))
                        .build(),
                organizationId,
                targetUserId,
                productCode,
                role
        );
    }

    @Transactional
    public ProductAccessEntity revokeAccess(
            UUID organizationId,
            UUID requestingUserId,
            UUID targetUserId,
            String productCode
    ) {
        tenantService.requireManager(organizationId, requestingUserId);
        requireActiveProduct(productCode);

        var id = new ProductAccessId(organizationId, targetUserId, productCode);
        var access = productAccessRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Product access does not exist."));
        access.setEnabled(false);
        return access;
    }

    @Transactional(readOnly = true)
    public ProductAccessResult checkAccess(
            UUID organizationId,
            UUID requestingUserId,
            UUID checkedUserId,
            String productCode
    ) {
        if (requestingUserId.equals(checkedUserId)) {
            tenantService.requireActiveMember(organizationId, requestingUserId);
        } else {
            tenantService.requireManager(organizationId, requestingUserId);
            tenantService.requireActiveMember(organizationId, checkedUserId);
        }
        requireActiveProduct(productCode);

        String payload = jdbcTemplate.queryForObject(
                "select platform.check_product_access(?, ?, ?)",
                String.class,
                organizationId,
                checkedUserId,
                productCode
        );

        try {
            return objectMapper.readValue(payload, ProductAccessResult.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Invalid product access result returned by database.", exception);
        }
    }

    private void requireActiveProduct(String productCode) {
        productRepository.findByCodeAndStatus(productCode, "active")
                .orElseThrow(() -> new BadRequestException("Product does not exist or is not active."));
    }
}

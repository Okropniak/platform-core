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

        return productRegistrationRepository
                .findByOrganizationIdAndUserIdAndProductCode(organizationId, userId, productCode)
                .map(existing -> {
                    existing.setAcceptedTermsVersion(termsVersion);
                    existing.setAcceptedPrivacyVersion(privacyVersion);
                    existing.setStatus("active");
                    return existing;
                })
                .orElseGet(() -> productRegistrationRepository.save(ProductRegistrationEntity.builder()
                        .organizationId(organizationId)
                        .userId(userId)
                        .productCode(productCode)
                        .acceptedTermsVersion(termsVersion)
                        .acceptedPrivacyVersion(privacyVersion)
                        .status("active")
                        .registeredAt(OffsetDateTime.now())
                        .build()));
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

        var id = new ProductAccessId(organizationId, targetUserId, productCode);
        return productAccessRepository.findById(id)
                .map(existing -> {
                    existing.setRole(role);
                    existing.setEnabled(true);
                    return existing;
                })
                .orElseGet(() -> productAccessRepository.save(ProductAccessEntity.builder()
                        .id(id)
                        .role(role)
                        .enabled(true)
                        .createdAt(OffsetDateTime.now())
                        .build()));
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

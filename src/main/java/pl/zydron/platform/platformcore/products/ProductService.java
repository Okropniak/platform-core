package pl.zydron.platform.platformcore.products;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.zydron.platform.platformcore.audit.AuditService;
import pl.zydron.platform.platformcore.common.BadRequestException;
import pl.zydron.platform.platformcore.tenants.TenantAccessPort;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Realizuje rejestrację użytkowników oraz nadawanie i sprawdzanie dostępu.
 *
 * <p>Operacje typu upsert są wykonywane przez parametryzowany SQL, aby
 * pojedyncza instrukcja PostgreSQL atomowo obsługiwała także równoczesne
 * żądania.</p>
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductRegistrationRepository productRegistrationRepository;
    private final ProductAccessRepository productAccessRepository;
    private final TenantAccessPort tenantService;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    /**
     * Rejestruje użytkownika w produkcie lub aktualizuje jego zgody.
     *
     * <p>{@code ON CONFLICT DO UPDATE} zapobiega błędowi przy równoczesnej lub
     * ponownej rejestracji tego samego użytkownika.</p>
     */
    public ProductRegistrationEntity registerUserToProduct(
            UUID organizationId,
            UUID userId,
            String productCode,
            String termsVersion,
            String privacyVersion
    ) {
        tenantService.requireActiveMember(organizationId, userId);
        requireActiveProduct(productCode);

        // JPA save wymagałoby schematu "odczytaj, potem wstaw", podatnego na
        // wyścig. PostgreSQL wykonuje poniższy upsert jako jedną operację.
        ProductRegistrationEntity registration = jdbcTemplate.queryForObject(
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
        auditService.record(
                organizationId,
                userId,
                productCode,
                "product_registered",
                "product_registration",
                registration.getId().toString(),
                Map.of("status", registration.getStatus())
        );
        return registration;
    }

    @Transactional
    /**
     * Nadaje dostęp członkowi organizacji albo aktualizuje istniejącą rolę.
     *
     * @throws pl.zydron.platform.platformcore.common.PlatformAccessDeniedException
     *         gdy wywołujący nie jest managerem
     */
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

        ProductAccessEntity access = jdbcTemplate.queryForObject(
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
        auditService.record(
                organizationId,
                requestingUserId,
                productCode,
                "product_access_granted",
                "product_access",
                targetUserId.toString(),
                Map.of("targetUserId", targetUserId.toString(), "role", access.getRole())
        );
        return access;
    }

    @Transactional
    /**
     * Logicznie odbiera dostęp przez ustawienie {@code enabled=false}.
     * Rekord pozostaje w bazie jako historia wcześniej nadanego dostępu.
     */
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
        auditService.record(
                organizationId,
                requestingUserId,
                productCode,
                "product_access_revoked",
                "product_access",
                targetUserId.toString(),
                Map.of("targetUserId", targetUserId.toString())
        );
        return access;
    }

    @Transactional(readOnly = true)
    /**
     * Sprawdza skuteczny dostęp przez funkcję PostgreSQL.
     *
     * <p>Funkcja zwraca JSON, ponieważ wynik obejmuje zarówno decyzję
     * {@code allowed}, jak i rolę. Jackson zamienia ten JSON na rekord Java.</p>
     */
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
            // Niepoprawny JSON oznacza złamanie kontraktu między migracją SQL
            // a kodem Java, dlatego jest to błąd wewnętrzny, a nie błąd klienta.
            throw new IllegalStateException("Invalid product access result returned by database.", exception);
        }
    }

    private void requireActiveProduct(String productCode) {
        productRepository.findByCodeAndStatus(productCode, "active")
                .orElseThrow(() -> new BadRequestException("Product does not exist or is not active."));
    }
}

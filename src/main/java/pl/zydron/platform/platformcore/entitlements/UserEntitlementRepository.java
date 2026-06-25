package pl.zydron.platform.platformcore.entitlements;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

/**
 * Repozytorium wyjątków i limitów przypisanych konkretnemu użytkownikowi.
 *
 * <p>Zapytanie {@link #findCurrent} uwzględnia aktywność oraz przedział
 * ważności, aby wygasły rekord nie wpływał na bieżącą autoryzację.</p>
 */
public interface UserEntitlementRepository extends JpaRepository<UserEntitlementEntity, UUID> {

    @Query(value = """
            select *
            from entitlement.user_entitlements entitlement
            where entitlement.organization_id = :organizationId
              and entitlement.user_id = :userId
              and entitlement.product_code = :productCode
              and entitlement.feature_code = :featureCode
              and (
                    (:metricCode is null and entitlement.metric_code is null)
                    or entitlement.metric_code = :metricCode
                  )
              and entitlement.enabled = true
              and entitlement.valid_from <= current_timestamp
              and (entitlement.valid_until is null or entitlement.valid_until > current_timestamp)
            order by entitlement.valid_from desc
            limit 1
            """, nativeQuery = true)
    Optional<UserEntitlementEntity> findCurrent(
            UUID organizationId,
            UUID userId,
            String productCode,
            String featureCode,
            String metricCode
    );
}

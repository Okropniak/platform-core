package pl.zydron.platform.platformcore.entitlements;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserEntitlementRepository extends JpaRepository<UserEntitlementEntity, UUID> {

    @Query("""
            select entitlement
            from UserEntitlementEntity entitlement
            where entitlement.organizationId = :organizationId
              and entitlement.userId = :userId
              and entitlement.productCode = :productCode
              and entitlement.featureCode = :featureCode
              and (
                    (:metricCode is null and entitlement.metricCode is null)
                    or entitlement.metricCode = :metricCode
                  )
              and entitlement.enabled = true
              and entitlement.validFrom <= current timestamp
              and (entitlement.validUntil is null or entitlement.validUntil > current timestamp)
            order by entitlement.validFrom desc
            limit 1
            """)
    Optional<UserEntitlementEntity> findCurrent(
            UUID organizationId,
            UUID userId,
            String productCode,
            String featureCode,
            String metricCode
    );
}

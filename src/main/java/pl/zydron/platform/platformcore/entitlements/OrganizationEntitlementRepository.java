package pl.zydron.platform.platformcore.entitlements;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationEntitlementRepository extends JpaRepository<OrganizationEntitlementEntity, UUID> {

    @Query("""
            select entitlement
            from OrganizationEntitlementEntity entitlement
            where entitlement.organizationId = :organizationId
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
    Optional<OrganizationEntitlementEntity> findCurrent(
            UUID organizationId,
            String productCode,
            String featureCode,
            String metricCode
    );

    @Modifying
    @Query("""
            delete from OrganizationEntitlementEntity entitlement
            where entitlement.organizationId = :organizationId
              and entitlement.productCode = :productCode
              and entitlement.source = 'plan'
            """)
    void deletePlanEntitlements(UUID organizationId, String productCode);
}

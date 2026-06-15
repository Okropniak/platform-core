package pl.zydron.platform.platformcore.entitlements;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationEntitlementRepository extends JpaRepository<OrganizationEntitlementEntity, UUID> {

    @Query(value = """
            select *
            from entitlement.organization_entitlements entitlement
            where entitlement.organization_id = :organizationId
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

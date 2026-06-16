package pl.zydron.platform.platformcore.usage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface UsageCounterRepository extends JpaRepository<UsageCounterEntity, UUID> {

    @Query("""
            select counter
            from UsageCounterEntity counter
            where counter.organizationId = :organizationId
              and counter.productCode = :productCode
              and counter.counterScope = 'organization'
            order by counter.periodStart desc, counter.metricCode asc
            """)
    List<UsageCounterEntity> findOrganizationSummary(UUID organizationId, String productCode);
}

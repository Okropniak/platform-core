package pl.zydron.platform.platformcore.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {

    Optional<SubscriptionEntity> findByOrganizationIdAndProductCode(UUID organizationId, String productCode);

    List<SubscriptionEntity> findByOrganizationIdOrderByProductCodeAsc(UUID organizationId);
}

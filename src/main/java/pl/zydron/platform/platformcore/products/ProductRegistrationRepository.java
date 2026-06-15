package pl.zydron.platform.platformcore.products;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductRegistrationRepository extends JpaRepository<ProductRegistrationEntity, UUID> {

    Optional<ProductRegistrationEntity> findByOrganizationIdAndUserIdAndProductCode(
            UUID organizationId,
            UUID userId,
            String productCode
    );
}

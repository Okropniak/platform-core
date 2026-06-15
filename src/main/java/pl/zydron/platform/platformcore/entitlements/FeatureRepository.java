package pl.zydron.platform.platformcore.entitlements;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FeatureRepository extends JpaRepository<FeatureEntity, UUID> {

    Optional<FeatureEntity> findByProductCodeAndFeatureCodeAndActive(String productCode, String featureCode, boolean active);
}

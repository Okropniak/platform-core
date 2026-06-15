package pl.zydron.platform.platformcore.entitlements;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MetricRepository extends JpaRepository<MetricEntity, UUID> {

    Optional<MetricEntity> findByProductCodeAndMetricCode(String productCode, String metricCode);
}

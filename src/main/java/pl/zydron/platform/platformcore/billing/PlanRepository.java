package pl.zydron.platform.platformcore.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<PlanEntity, UUID> {

    Optional<PlanEntity> findByProductCodeAndPlanCodeAndActiveTrue(String productCode, String planCode);

    List<PlanEntity> findByProductCodeAndActiveTrueOrderByPriceAsc(String productCode);
}

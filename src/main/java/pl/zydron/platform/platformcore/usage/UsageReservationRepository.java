package pl.zydron.platform.platformcore.usage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsageReservationRepository extends JpaRepository<UsageReservationEntity, UUID> {

    Optional<UsageReservationEntity> findByIdAndUserId(UUID id, UUID userId);
}

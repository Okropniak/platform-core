package pl.zydron.platform.platformcore.usage;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsageEventRepository extends JpaRepository<UsageEventEntity, Long> {
}

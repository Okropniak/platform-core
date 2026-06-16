package pl.zydron.platform.platformcore.usage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(schema = "usage", name = "usage_events")
public class UsageEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID organizationId;

    private UUID userId;

    @Column(nullable = false)
    private String productCode;

    @Column(nullable = false)
    private String metricCode;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String counterScope;

    private UUID reservationId;

    private String idempotencyKey;

    @Column(nullable = false)
    private OffsetDateTime createdAt;
}

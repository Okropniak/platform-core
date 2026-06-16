package pl.zydron.platform.platformcore.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
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
@Table(schema = "billing", name = "plans")
public class PlanEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String productCode;

    @Column(nullable = false)
    private String planCode;

    @Column(nullable = false)
    private String name;

    private BigDecimal price;

    private String currency;

    private String billingPeriod;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private OffsetDateTime createdAt;
}

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

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(schema = "billing", name = "subscriptions")
public class SubscriptionEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String productCode;

    @Column(nullable = false)
    private String planCode;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String provider;

    private String providerCustomerId;

    private String providerSubscriptionId;

    private OffsetDateTime currentPeriodStart;

    private OffsetDateTime currentPeriodEnd;

    private OffsetDateTime cancelledAt;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;
}

package pl.zydron.platform.platformcore.entitlements;

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
@Table(schema = "entitlement", name = "organization_entitlements")
/**
 * Prawo do funkcji lub limit obowiązujący całą organizację.
 *
 * <p>{@code metricCode=null} oznacza funkcję typu włączona/wyłączona.
 * Niepusta metryka łączy funkcję z liczbowym limitem. Pola ważności pozwalają
 * zachować historię zmian i wyłączyć rekord bez jego usuwania.</p>
 */
public class OrganizationEntitlementEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String productCode;

    @Column(nullable = false)
    private String featureCode;

    private String metricCode;

    @Column(nullable = false)
    private boolean enabled;

    private BigDecimal limitValue;

    private String period;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private OffsetDateTime validFrom;

    private OffsetDateTime validUntil;
}

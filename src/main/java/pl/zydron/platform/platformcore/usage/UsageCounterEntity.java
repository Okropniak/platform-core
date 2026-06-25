package pl.zydron.platform.platformcore.usage;

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
@Table(schema = "usage", name = "usage_counters")
/**
 * Zagregowany licznik użycia metryki w jednym okresie.
 *
 * <p>{@code usedValue} opisuje użycie zakończone, a {@code reservedValue}
 * ilość tymczasowo zablokowaną. {@code counterScope} rozróżnia licznik całej
 * organizacji od licznika konkretnego użytkownika.</p>
 */
public class UsageCounterEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID organizationId;

    private UUID userId;

    @Column(nullable = false)
    private String productCode;

    @Column(nullable = false)
    private String metricCode;

    @Column(nullable = false)
    private OffsetDateTime periodStart;

    @Column(nullable = false)
    private OffsetDateTime periodEnd;

    @Column(nullable = false)
    private BigDecimal usedValue;

    @Column(nullable = false)
    private BigDecimal reservedValue;

    @Column(nullable = false)
    private String counterScope;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;
}

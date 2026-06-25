package pl.zydron.platform.platformcore.usage;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Uproszczony widok licznika organizacji zwracany przez endpoint podsumowania.
 */
public record UsageSummaryItem(
        String metricCode,
        OffsetDateTime periodStart,
        OffsetDateTime periodEnd,
        BigDecimal usedValue,
        BigDecimal reservedValue
) {
}

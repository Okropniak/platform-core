package pl.zydron.platform.platformcore.usage;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record UsageSummaryItem(
        String metricCode,
        OffsetDateTime periodStart,
        OffsetDateTime periodEnd,
        BigDecimal usedValue,
        BigDecimal reservedValue
) {
}

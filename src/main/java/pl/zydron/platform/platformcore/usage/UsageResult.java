package pl.zydron.platform.platformcore.usage;

import java.math.BigDecimal;

public record UsageResult(
        boolean accepted,
        String reason,
        BigDecimal used,
        BigDecimal reserved,
        BigDecimal limit,
        BigDecimal remaining
) {
}

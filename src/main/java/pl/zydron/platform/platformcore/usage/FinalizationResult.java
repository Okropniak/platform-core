package pl.zydron.platform.platformcore.usage;

import java.math.BigDecimal;
import java.util.UUID;

public record FinalizationResult(
        boolean finalized,
        String reason,
        UUID reservationId,
        BigDecimal actualAmount,
        BigDecimal releasedAmount,
        BigDecimal used,
        BigDecimal reserved
) {
}

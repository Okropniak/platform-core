package pl.zydron.platform.platformcore.usage;

import java.math.BigDecimal;
import java.util.UUID;

public record ReservationResult(
        boolean accepted,
        String reason,
        UUID reservationId,
        BigDecimal reserved,
        BigDecimal used,
        BigDecimal totalReserved,
        BigDecimal limit,
        BigDecimal remaining
) {
}

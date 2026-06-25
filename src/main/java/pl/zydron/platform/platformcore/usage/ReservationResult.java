package pl.zydron.platform.platformcore.usage;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Wynik próby zarezerwowania części limitu.
 *
 * <p>Przy sukcesie {@code reservationId} jest potrzebny do późniejszego
 * wywołania finalizacji.</p>
 */
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

package pl.zydron.platform.platformcore.usage;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Wynik zamiany rezerwacji na faktycznie wykorzystaną ilość.
 *
 * <p>{@code releasedAmount} oznacza część rezerwacji zwróconą do dostępnego
 * limitu.</p>
 */
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

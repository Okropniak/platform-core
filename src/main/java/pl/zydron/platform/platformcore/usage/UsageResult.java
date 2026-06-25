package pl.zydron.platform.platformcore.usage;

import java.math.BigDecimal;

/**
 * Wynik natychmiastowego naliczenia użycia.
 *
 * <p>Gdy {@code accepted=false}, pole {@code reason} wyjaśnia odrzucenie,
 * a wartości liczników opisują stan bez wykonania podwójnego naliczenia.</p>
 */
public record UsageResult(
        boolean accepted,
        String reason,
        BigDecimal used,
        BigDecimal reserved,
        BigDecimal limit,
        BigDecimal remaining
) {
}

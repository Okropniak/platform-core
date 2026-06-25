package pl.zydron.platform.platformcore.entitlements;

import java.math.BigDecimal;

/**
 * Skuteczny wynik autoryzacji funkcji i jej limitu.
 *
 * @param enabled czy funkcja jest dostępna
 * @param limitValue maksymalna wartość; {@code null} oznacza brak limitu liczbowego
 * @param period okres resetowania limitu
 * @param source źródło decyzji, np. plan lub ręczny override
 */
public record EntitlementLimit(boolean enabled, BigDecimal limitValue, String period, String source) {
}

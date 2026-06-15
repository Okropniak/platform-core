package pl.zydron.platform.platformcore.entitlements;

import java.math.BigDecimal;

public record EntitlementLimit(boolean enabled, BigDecimal limitValue, String period, String source) {
}

package pl.zydron.platform.platformcore.usage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.zydron.platform.platformcore.common.JwtUser;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UsageController {

    private final UsageService usageService;

    @PostMapping("/api/usage/consume")
    UsageResult consume(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConsumeUsageRequest request) {
        return usageService.consumeUsage(
                request.organizationId(),
                JwtUser.userId(jwt),
                request.productCode(),
                request.metricCode(),
                request.amount(),
                request.idempotencyKey()
        );
    }

    @PostMapping("/api/usage/reserve")
    ReservationResult reserve(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReserveUsageRequest request) {
        return usageService.reserveUsage(
                request.organizationId(),
                JwtUser.userId(jwt),
                request.productCode(),
                request.metricCode(),
                request.amount(),
                request.idempotencyKey()
        );
    }

    @PostMapping("/api/usage/finalize")
    FinalizationResult finalize(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody FinalizeUsageRequest request) {
        return usageService.finalizeUsage(request.reservationId(), JwtUser.userId(jwt), request.actualAmount());
    }

    @GetMapping("/api/organizations/{id}/usage/{productCode}")
    List<UsageSummaryItem> summary(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable String productCode
    ) {
        return usageService.getUsageSummary(id, JwtUser.userId(jwt), productCode);
    }

    public record ConsumeUsageRequest(
            @NotNull UUID organizationId,
            @NotBlank String productCode,
            @NotBlank String metricCode,
            @NotNull @Positive BigDecimal amount,
            @NotBlank String idempotencyKey
    ) {
    }

    public record ReserveUsageRequest(
            @NotNull UUID organizationId,
            @NotBlank String productCode,
            @NotBlank String metricCode,
            @NotNull @Positive BigDecimal amount,
            @NotBlank String idempotencyKey
    ) {
    }

    public record FinalizeUsageRequest(
            @NotNull UUID reservationId,
            @NotNull @PositiveOrZero BigDecimal actualAmount
    ) {
    }
}

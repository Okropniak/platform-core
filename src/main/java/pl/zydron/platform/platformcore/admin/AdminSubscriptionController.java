package pl.zydron.platform.platformcore.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.zydron.platform.platformcore.billing.BillingService;
import pl.zydron.platform.platformcore.billing.SubscriptionEntity;
import pl.zydron.platform.platformcore.common.JwtUser;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/admin/organizations/{id}/subscriptions")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSubscriptionController {

    private final BillingService billingService;

    @PostMapping
    SubscriptionResponse createManualSubscription(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateSubscriptionRequest request
    ) {
        return SubscriptionResponse.from(billingService.createManualSubscriptionAsAdmin(
                id,
                JwtUser.userId(jwt),
                request.productCode(),
                request.planCode()
        ));
    }

    @PutMapping("/{productCode}")
    SubscriptionResponse changeSubscription(
            @PathVariable UUID id,
            @PathVariable String productCode,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangeSubscriptionRequest request
    ) {
        return SubscriptionResponse.from(billingService.changeSubscriptionAsAdmin(
                id,
                JwtUser.userId(jwt),
                productCode,
                request.planCode(),
                request.status()
        ));
    }

    public record CreateSubscriptionRequest(
            @NotBlank String productCode,
            @NotBlank String planCode
    ) {
    }

    public record ChangeSubscriptionRequest(
            @NotBlank String planCode,
            @NotBlank
            @Pattern(regexp = "trial|active|past_due|cancelled|expired|manual") String status
    ) {
    }

    public record SubscriptionResponse(
            UUID id,
            UUID organizationId,
            String productCode,
            String planCode,
            String status,
            String provider,
            OffsetDateTime currentPeriodStart,
            OffsetDateTime currentPeriodEnd,
            OffsetDateTime cancelledAt
    ) {
        static SubscriptionResponse from(SubscriptionEntity subscription) {
            return new SubscriptionResponse(
                    subscription.getId(),
                    subscription.getOrganizationId(),
                    subscription.getProductCode(),
                    subscription.getPlanCode(),
                    subscription.getStatus(),
                    subscription.getProvider(),
                    subscription.getCurrentPeriodStart(),
                    subscription.getCurrentPeriodEnd(),
                    subscription.getCancelledAt()
            );
        }
    }
}

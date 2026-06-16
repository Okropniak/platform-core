package pl.zydron.platform.platformcore.billing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.zydron.platform.platformcore.common.JwtUser;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @PostMapping("/api/organizations/{organizationId}/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    SubscriptionResponse createManualSubscription(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID organizationId,
            @Valid @RequestBody CreateSubscriptionRequest request
    ) {
        return SubscriptionResponse.from(billingService.createManualSubscription(
                organizationId,
                JwtUser.userId(jwt),
                request.productCode(),
                request.planCode()
        ));
    }

    @DeleteMapping("/api/organizations/{organizationId}/subscriptions/{productCode}")
    SubscriptionResponse cancelSubscription(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID organizationId,
            @PathVariable String productCode
    ) {
        return SubscriptionResponse.from(billingService.cancelSubscription(
                organizationId,
                JwtUser.userId(jwt),
                productCode
        ));
    }

    @GetMapping("/api/organizations/{organizationId}/subscriptions")
    List<SubscriptionResponse> getSubscriptions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID organizationId
    ) {
        return billingService.getSubscriptions(organizationId, JwtUser.userId(jwt)).stream()
                .map(SubscriptionResponse::from)
                .toList();
    }

    public record CreateSubscriptionRequest(
            @NotBlank String productCode,
            @NotBlank String planCode
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

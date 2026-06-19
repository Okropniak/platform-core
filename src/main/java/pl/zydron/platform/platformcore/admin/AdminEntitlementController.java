package pl.zydron.platform.platformcore.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.zydron.platform.platformcore.common.JwtUser;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/admin/organizations/{id}/entitlements")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminEntitlementController {

    private final AdminEntitlementService adminEntitlementService;

    @PutMapping
    AdminEntitlementService.EntitlementOverrideResult overrideEntitlement(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OverrideEntitlementRequest request
    ) {
        return adminEntitlementService.overrideOrganizationEntitlement(
                id,
                JwtUser.userId(jwt),
                request.productCode(),
                request.featureCode(),
                request.metricCode(),
                request.enabled(),
                request.limitValue(),
                request.period(),
                request.source()
        );
    }

    public record OverrideEntitlementRequest(
            @NotBlank String productCode,
            @NotBlank String featureCode,
            String metricCode,
            boolean enabled,
            BigDecimal limitValue,
            @Pattern(regexp = "daily|monthly|yearly|lifetime") String period,
            @Pattern(regexp = "manual|promo|enterprise|admin_override") String source
    ) {
    }
}

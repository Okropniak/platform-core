package pl.zydron.platform.platformcore.entitlements;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import pl.zydron.platform.platformcore.common.JwtUser;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class EntitlementController {

    private final EntitlementService entitlementService;

    @GetMapping("/api/organizations/{id}/entitlements/{productCode}")
    EntitlementService.EntitlementsResponse getEntitlements(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable String productCode
    ) {
        return entitlementService.getEntitlements(id, JwtUser.userId(jwt), productCode);
    }
}

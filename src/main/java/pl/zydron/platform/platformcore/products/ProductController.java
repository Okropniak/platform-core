package pl.zydron.platform.platformcore.products;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.zydron.platform.platformcore.common.JwtUser;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping("/api/products/{code}/register")
    @ResponseStatus(HttpStatus.CREATED)
    ProductRegistrationResponse register(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String code,
            @Valid @RequestBody RegisterProductRequest request
    ) {
        return ProductRegistrationResponse.from(productService.registerUserToProduct(
                request.organizationId(),
                JwtUser.userId(jwt),
                code,
                request.termsVersion(),
                request.privacyVersion()
        ));
    }

    @PostMapping("/api/products/{code}/access")
    @ResponseStatus(HttpStatus.CREATED)
    ProductAccessResponse grantAccess(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String code,
            @Valid @RequestBody GrantProductAccessRequest request
    ) {
        return ProductAccessResponse.from(productService.grantAccess(
                request.organizationId(),
                JwtUser.userId(jwt),
                request.userId(),
                code,
                request.role()
        ));
    }

    @DeleteMapping("/api/organizations/{organizationId}/products/{code}/access/{userId}")
    ProductAccessResponse revokeAccess(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID organizationId,
            @PathVariable String code,
            @PathVariable UUID userId
    ) {
        return ProductAccessResponse.from(productService.revokeAccess(
                organizationId,
                JwtUser.userId(jwt),
                userId,
                code
        ));
    }

    @GetMapping("/api/products/{code}/access")
    ProductAccessCheckResponse checkAccess(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String code,
            @RequestParam UUID organizationId,
            @RequestParam(required = false) UUID userId
    ) {
        var requestingUserId = JwtUser.userId(jwt);
        var checkedUserId = userId == null ? requestingUserId : userId;
        var result = productService.checkAccess(organizationId, requestingUserId, checkedUserId, code);
        return new ProductAccessCheckResponse(result.allowed(), result.role());
    }

    public record RegisterProductRequest(
            @NotNull UUID organizationId,
            @Size(max = 80) String termsVersion,
            @Size(max = 80) String privacyVersion
    ) {
    }

    public record GrantProductAccessRequest(
            @NotNull UUID organizationId,
            @NotNull UUID userId,
            @NotBlank @Pattern(regexp = "owner|admin|user|viewer") String role
    ) {
    }

    public record ProductRegistrationResponse(
            UUID id,
            UUID organizationId,
            UUID userId,
            String productCode,
            String status,
            OffsetDateTime registeredAt
    ) {
        static ProductRegistrationResponse from(ProductRegistrationEntity registration) {
            return new ProductRegistrationResponse(
                    registration.getId(),
                    registration.getOrganizationId(),
                    registration.getUserId(),
                    registration.getProductCode(),
                    registration.getStatus(),
                    registration.getRegisteredAt()
            );
        }
    }

    public record ProductAccessResponse(
            UUID organizationId,
            UUID userId,
            String productCode,
            String role,
            boolean enabled
    ) {
        static ProductAccessResponse from(ProductAccessEntity access) {
            return new ProductAccessResponse(
                    access.getId().getOrganizationId(),
                    access.getId().getUserId(),
                    access.getId().getProductCode(),
                    access.getRole(),
                    access.isEnabled()
            );
        }
    }

    public record ProductAccessCheckResponse(boolean allowed, String role) {
    }
}

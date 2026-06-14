package pl.zydron.platform.platformcore.tenants;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/organizations")
public class TenantController {

    private final TenantService tenantService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OrganizationResponse createOrganization(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateOrganizationRequest request
    ) {
        return OrganizationResponse.from(tenantService.createOrganization(userId(jwt), request.name(), request.type()));
    }

    @GetMapping
    List<OrganizationResponse> getOrganizations(@AuthenticationPrincipal Jwt jwt) {
        return tenantService.getOrganizationsForUser(userId(jwt)).stream()
                .map(OrganizationResponse::from)
                .toList();
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    MemberResponse addMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody AddMemberRequest request
    ) {
        return MemberResponse.from(tenantService.addMember(id, userId(jwt), request.userId(), request.role()));
    }

    @ExceptionHandler(TenantAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    ErrorResponse handleAccessDenied(TenantAccessDeniedException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record CreateOrganizationRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Pattern(regexp = "individual|company") String type
    ) {
    }

    public record AddMemberRequest(
            @NotNull UUID userId,
            @NotBlank @Pattern(regexp = "owner|admin|member") String role
    ) {
    }

    public record OrganizationResponse(
            UUID id,
            String name,
            String type,
            UUID createdBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        static OrganizationResponse from(OrganizationEntity organization) {
            return new OrganizationResponse(
                    organization.getId(),
                    organization.getName(),
                    organization.getType(),
                    organization.getCreatedBy(),
                    organization.getCreatedAt(),
                    organization.getUpdatedAt()
            );
        }
    }

    public record MemberResponse(UUID organizationId, UUID userId, String role, String status) {
        static MemberResponse from(OrganizationMemberEntity member) {
            return new MemberResponse(
                    member.getId().getOrganizationId(),
                    member.getId().getUserId(),
                    member.getRole(),
                    member.getStatus()
            );
        }
    }

    public record ErrorResponse(String message) {
    }
}

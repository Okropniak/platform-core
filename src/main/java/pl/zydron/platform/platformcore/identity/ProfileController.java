package pl.zydron.platform.platformcore.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.zydron.platform.platformcore.common.JwtUser;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Udostępnia zalogowanemu użytkownikowi operacje na jego profilu aplikacyjnym.
 *
 * <p>Kontroler nie przyjmuje identyfikatora użytkownika w adresie ani w JSON.
 * UUID jest zawsze odczytywany ze zweryfikowanego tokenu JWT, dlatego klient
 * nie może przez zmianę żądania edytować profilu innej osoby.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    /**
     * Tworzy profil albo aktualizuje jego nazwę.
     *
     * <p>Operacja jest idempotentna względem użytkownika: wiele wywołań nie
     * tworzy wielu profili, ponieważ tabela ma unikalny {@code user_id}, a
     * serwis obsługuje również równoczesne próby zapisu.</p>
     */
    @PutMapping
    ProfileResponse upsertProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpsertProfileRequest request
    ) {
        return ProfileResponse.from(
                profileService.upsertProfile(JwtUser.userId(jwt), request.displayName())
        );
    }

    /**
     * Dane, które użytkownik może zmienić w swoim profilu.
     */
    public record UpsertProfileRequest(
            @NotBlank @Size(max = 200) String displayName
    ) {
    }

    /**
     * Stabilny kontrakt HTTP niezależny od szczegółów encji JPA.
     */
    public record ProfileResponse(
            UUID id,
            UUID userId,
            String displayName,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        static ProfileResponse from(ProfileEntity profile) {
            return new ProfileResponse(
                    profile.getId(),
                    profile.getUserId(),
                    profile.getDisplayName(),
                    profile.getCreatedAt(),
                    profile.getUpdatedAt()
            );
        }
    }
}

package pl.zydron.platform.platformcore.identity;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileControllerTests {

    private final ProfileService profileService = mock(ProfileService.class);
    private final ProfileController profileController = new ProfileController(profileService);

    @Test
    void upsertProfileUsesAuthenticatedUserId() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        ProfileEntity profile = ProfileEntity.builder()
                .id(profileId)
                .userId(userId)
                .displayName("Jan Kowalski")
                .createdAt(now)
                .updatedAt(now)
                .build();
        when(profileService.upsertProfile(userId, "Jan Kowalski")).thenReturn(profile);

        ProfileController.ProfileResponse response = profileController.upsertProfile(
                jwt,
                new ProfileController.UpsertProfileRequest("Jan Kowalski")
        );

        assertThat(response.id()).isEqualTo(profileId);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.displayName()).isEqualTo("Jan Kowalski");
        verify(profileService).upsertProfile(userId, "Jan Kowalski");
    }

    @Test
    void profileRequestRejectsBlankAndOverlongDisplayName() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            assertThat(validator.validate(new ProfileController.UpsertProfileRequest(" ")))
                    .isNotEmpty();
            assertThat(validator.validate(new ProfileController.UpsertProfileRequest("a".repeat(201))))
                    .isNotEmpty();
            assertThat(validator.validate(new ProfileController.UpsertProfileRequest("Jan Kowalski")))
                    .isEmpty();
        }
    }
}

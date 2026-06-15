package pl.zydron.platform.platformcore.identity;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfileServiceTests {

    private final ProfileRepository profileRepository = mock(ProfileRepository.class);
    private final ProfileService profileService = new ProfileService(profileRepository);

    @Test
    void upsertProfileReturnsExistingProfileAfterConcurrentInsertConflict() {
        UUID userId = UUID.randomUUID();
        ProfileEntity existing = ProfileEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .displayName("Existing")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(profileRepository.findByUserId(userId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(profileRepository.save(any(ProfileEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate user_id"));

        ProfileEntity result = profileService.upsertProfile(userId, "New");

        assertThat(result).isSameAs(existing);
    }
}

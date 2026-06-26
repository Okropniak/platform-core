package pl.zydron.platform.platformcore.identity;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileServiceTests {

    private final ProfileRepository profileRepository = mock(ProfileRepository.class);
    private final ProfileService profileService = new ProfileService(profileRepository);

    @Test
    void upsertProfileUpdatesExistingDisplayName() {
        UUID userId = UUID.randomUUID();
        OffsetDateTime originalUpdatedAt = OffsetDateTime.now().minusDays(1);
        ProfileEntity existing = ProfileEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .displayName("Old Name")
                .createdAt(OffsetDateTime.now().minusDays(2))
                .updatedAt(originalUpdatedAt)
                .build();
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        ProfileEntity result = profileService.upsertProfile(userId, "New Name");

        assertThat(result).isSameAs(existing);
        assertThat(result.getDisplayName()).isEqualTo("New Name");
        assertThat(result.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

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

    @Test
    void ensureProfileExistsDoesNotOverwriteExistingDisplayName() {
        UUID userId = UUID.randomUUID();
        ProfileEntity existing = ProfileEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .displayName("Existing")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        ProfileEntity result = profileService.ensureProfileExists(userId, "Fallback");

        assertThat(result).isSameAs(existing);
        assertThat(result.getDisplayName()).isEqualTo("Existing");
        verify(profileRepository, never()).save(any(ProfileEntity.class));
    }

    @Test
    void ensureProfileExistsCreatesMissingProfile() {
        UUID userId = UUID.randomUUID();
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(profileRepository.save(any(ProfileEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProfileEntity result = profileService.ensureProfileExists(userId, "New User");

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getDisplayName()).isEqualTo("New User");
        verify(profileRepository).save(any(ProfileEntity.class));
    }
}

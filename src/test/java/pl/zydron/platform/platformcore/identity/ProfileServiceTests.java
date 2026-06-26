package pl.zydron.platform.platformcore.identity;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileServiceTests {

    private final ProfileRepository profileRepository = mock(ProfileRepository.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ProfileService profileService = new ProfileService(profileRepository, jdbcTemplate);

    @Test
    void upsertProfileUsesAtomicConflictUpdate() {
        UUID userId = UUID.randomUUID();
        ProfileEntity upserted = ProfileEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .displayName("New Name")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ProfileEntity>>any(),
                any(), any(), any(), any()))
                .thenReturn(List.of(upserted));

        ProfileEntity result = profileService.upsertProfile(userId, "New Name");

        assertThat(result).isSameAs(upserted);
        assertThat(result.getDisplayName()).isEqualTo("New Name");
        verify(profileRepository, never()).save(any(ProfileEntity.class));
        verify(profileRepository, never()).findByUserId(userId);
    }

    @Test
    void ensureProfileExistsReturnsCreatedProfileWithoutRepositorySave() {
        UUID userId = UUID.randomUUID();
        ProfileEntity created = ProfileEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .displayName("New User")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ProfileEntity>>any(),
                any(), any(), any(), any()))
                .thenReturn(List.of(created));

        ProfileEntity result = profileService.ensureProfileExists(userId, "New User");

        assertThat(result).isSameAs(created);
        verify(profileRepository, never()).save(any(ProfileEntity.class));
        verify(profileRepository, never()).findByUserId(userId);
    }

    @Test
    void ensureProfileExistsReturnsExistingProfileWithoutOverwritingDisplayName() {
        UUID userId = UUID.randomUUID();
        ProfileEntity existing = ProfileEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .displayName("Existing")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ProfileEntity>>any(),
                any(), any(), any(), any()))
                .thenReturn(List.of());
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        ProfileEntity result = profileService.ensureProfileExists(userId, "Fallback");

        assertThat(result).isSameAs(existing);
        assertThat(result.getDisplayName()).isEqualTo("Existing");
        verify(profileRepository, never()).save(any(ProfileEntity.class));
    }

    @Test
    void ensureProfileExistsUsesDoNothingInsteadOfUpdateOnConflict() {
        UUID userId = UUID.randomUUID();
        ProfileEntity existing = ProfileEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .displayName("Existing")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ProfileEntity>>any(),
                any(), any(), any(), any()))
                .thenReturn(List.of());
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        profileService.ensureProfileExists(userId, "Fallback");

        verify(jdbcTemplate).query(
                org.mockito.ArgumentMatchers.contains("on conflict (user_id) do nothing"),
                org.mockito.ArgumentMatchers.<RowMapper<ProfileEntity>>any(),
                any(),
                any(),
                any(),
                any()
        );
    }
}

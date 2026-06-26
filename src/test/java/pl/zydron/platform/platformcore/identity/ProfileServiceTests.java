package pl.zydron.platform.platformcore.identity;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        when(jdbcTemplate.query(
                org.mockito.ArgumentMatchers.contains("on conflict (user_id) do update"),
                org.mockito.ArgumentMatchers.<PreparedStatementSetter>any(),
                org.mockito.ArgumentMatchers.<RowMapper<ProfileEntity>>any()
        ))
                .thenReturn(List.of(upserted));

        ProfileEntity result = profileService.upsertProfile(userId, "New Name");

        assertThat(result).isSameAs(upserted);
        assertThat(result.getDisplayName()).isEqualTo("New Name");
        verify(profileRepository, never()).save(any(ProfileEntity.class));
        verify(profileRepository, never()).findByUserId(userId);
    }

    @Test
    void upsertProfileThrowsContextualExceptionWhenSqlReturnsNoRows() {
        UUID userId = UUID.randomUUID();
        when(jdbcTemplate.query(
                org.mockito.ArgumentMatchers.contains("on conflict (user_id) do update"),
                org.mockito.ArgumentMatchers.<PreparedStatementSetter>any(),
                org.mockito.ArgumentMatchers.<RowMapper<ProfileEntity>>any()
        ))
                .thenReturn(List.of());

        assertThatThrownBy(() -> profileService.upsertProfile(userId, "New Name"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("upsertProfile returned no row")
                .hasMessageContaining(userId.toString());
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
        when(jdbcTemplate.query(
                org.mockito.ArgumentMatchers.contains("on conflict (user_id) do nothing"),
                org.mockito.ArgumentMatchers.<PreparedStatementSetter>any(),
                org.mockito.ArgumentMatchers.<RowMapper<ProfileEntity>>any()
        ))
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
        when(jdbcTemplate.query(
                org.mockito.ArgumentMatchers.contains("on conflict (user_id) do nothing"),
                org.mockito.ArgumentMatchers.<PreparedStatementSetter>any(),
                org.mockito.ArgumentMatchers.<RowMapper<ProfileEntity>>any()
        ))
                .thenReturn(List.of());
        when(jdbcTemplate.query(
                org.mockito.ArgumentMatchers.contains("from platform.profiles"),
                org.mockito.ArgumentMatchers.<PreparedStatementSetter>any(),
                org.mockito.ArgumentMatchers.<RowMapper<ProfileEntity>>any()
        ))
                .thenReturn(List.of(existing));

        ProfileEntity result = profileService.ensureProfileExists(userId, "Fallback");

        assertThat(result).isSameAs(existing);
        assertThat(result.getDisplayName()).isEqualTo("Existing");
        verify(profileRepository, never()).save(any(ProfileEntity.class));
        verify(profileRepository, never()).findByUserId(userId);
    }

    @Test
    void ensureProfileExistsThrowsContextualExceptionWhenInsertAndFallbackReturnNoRows() {
        UUID userId = UUID.randomUUID();
        when(jdbcTemplate.query(
                org.mockito.ArgumentMatchers.contains("on conflict (user_id) do nothing"),
                org.mockito.ArgumentMatchers.<PreparedStatementSetter>any(),
                org.mockito.ArgumentMatchers.<RowMapper<ProfileEntity>>any()
        ))
                .thenReturn(List.of());
        when(jdbcTemplate.query(
                org.mockito.ArgumentMatchers.contains("from platform.profiles"),
                org.mockito.ArgumentMatchers.<PreparedStatementSetter>any(),
                org.mockito.ArgumentMatchers.<RowMapper<ProfileEntity>>any()
        ))
                .thenReturn(List.of());

        assertThatThrownBy(() -> profileService.ensureProfileExists(userId, "Fallback"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Profile not found after ensureProfileExists")
                .hasMessageContaining(userId.toString());

        verify(profileRepository, never()).findByUserId(userId);
    }
}

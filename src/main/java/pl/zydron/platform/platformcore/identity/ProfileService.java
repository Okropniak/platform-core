package pl.zydron.platform.platformcore.identity;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public Optional<ProfileEntity> findByUserId(UUID userId) {
        return profileRepository.findByUserId(userId);
    }

    @Transactional
    public ProfileEntity upsertProfile(UUID userId, String displayName) {
        var now = OffsetDateTime.now();
        return profileRepository.findByUserId(userId)
                .map(profile -> {
                    profile.setDisplayName(displayName);
                    profile.setUpdatedAt(now);
                    return profile;
                })
                .orElseGet(() -> createProfile(userId, displayName, now));
    }

    private ProfileEntity createProfile(UUID userId, String displayName, OffsetDateTime now) {
        try {
            return profileRepository.save(ProfileEntity.builder()
                    .userId(userId)
                    .displayName(displayName)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        } catch (DataIntegrityViolationException exception) {
            return profileRepository.findByUserId(userId)
                    .orElseThrow(() -> exception);
        }
    }
}

package pl.zydron.platform.platformcore.identity;

import lombok.RequiredArgsConstructor;
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
                .orElseGet(() -> profileRepository.save(ProfileEntity.builder()
                        .userId(userId)
                        .displayName(displayName)
                        .createdAt(now)
                        .updatedAt(now)
                        .build()));
    }
}

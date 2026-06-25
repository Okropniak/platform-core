package pl.zydron.platform.platformcore.identity;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Udostępnia operacje na profilu użytkownika niezależne od danych logowania.
 *
 * <p>Konto i hasło są zarządzane przez Supabase Auth. Ten serwis odpowiada
 * wyłącznie za dane aplikacyjne zapisane w tabeli {@code platform.profiles}.</p>
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;

    @Transactional(readOnly = true)
    /**
     * Wyszukuje profil powiązany z użytkownikiem Supabase.
     *
     * @param userId UUID z pola {@code sub} tokenu
     * @return profil lub pusty wynik, jeżeli profil nie został jeszcze utworzony
     */
    public Optional<ProfileEntity> findByUserId(UUID userId) {
        return profileRepository.findByUserId(userId);
    }

    @Transactional
    /**
     * Tworzy profil albo aktualizuje jego nazwę, gdy profil już istnieje.
     *
     * <p>Cała operacja jest transakcyjna. Dwie równoczesne próby utworzenia
     * profilu mogą obie najpierw nie znaleźć rekordu, dlatego prywatna metoda
     * tworząca obsługuje również konflikt unikalnego {@code user_id}.</p>
     */
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
            // Inna transakcja mogła utworzyć profil pomiędzy odczytem a zapisem.
            // W takim przypadku zwracamy rekord zwycięskiej transakcji.
            return profileRepository.findByUserId(userId)
                    .orElseThrow(() -> exception);
        }
    }
}

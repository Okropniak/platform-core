package pl.zydron.platform.platformcore.identity;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Udostepnia operacje na profilu uzytkownika niezalezne od danych logowania.
 *
 * <p>Konto i haslo sa zarzadzane przez Supabase Auth. Ten serwis odpowiada
 * wylacznie za dane aplikacyjne zapisane w tabeli {@code platform.profiles}.</p>
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final RowMapper<ProfileEntity> PROFILE_ROW_MAPPER = (resultSet, rowNum) -> ProfileEntity.builder()
            .id(resultSet.getObject("id", UUID.class))
            .userId(resultSet.getObject("user_id", UUID.class))
            .displayName(resultSet.getString("display_name"))
            .createdAt(resultSet.getObject("created_at", OffsetDateTime.class))
            .updatedAt(resultSet.getObject("updated_at", OffsetDateTime.class))
            .build();

    private final ProfileRepository profileRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Wyszukuje profil powiazany z uzytkownikiem Supabase.
     *
     * @param userId UUID z pola {@code sub} tokenu
     * @return profil lub pusty wynik, jezeli profil nie zostal jeszcze utworzony
     */
    @Transactional(readOnly = true)
    public Optional<ProfileEntity> findByUserId(UUID userId) {
        return profileRepository.findByUserId(userId);
    }

    /**
     * Tworzy profil albo aktualizuje jego nazwe, gdy profil juz istnieje.
     *
     * <p>Metoda uzywa jednego atomowego polecenia PostgreSQL
     * {@code INSERT ... ON CONFLICT DO UPDATE}. Dzieki temu dwie rownolegle
     * prosby tego samego uzytkownika nie trafiaja w opozniony flush Hibernate
     * i nie koncza sie bledem 500 przy naruszeniu unikalnego {@code user_id}.</p>
     */
    @Transactional
    public ProfileEntity upsertProfile(UUID userId, String displayName) {
        OffsetDateTime now = OffsetDateTime.now();
        return jdbcTemplate.query("""
                        insert into platform.profiles(user_id, display_name, created_at, updated_at)
                        values (?, ?, ?, ?)
                        on conflict (user_id) do update
                            set display_name = excluded.display_name,
                                updated_at = excluded.updated_at
                        returning id, user_id, display_name, created_at, updated_at
                        """,
                PROFILE_ROW_MAPPER,
                userId,
                displayName,
                now,
                now
        ).getFirst();
    }

    /**
     * Zapewnia, ze uzytkownik ma profil, ale nie zmienia istniejacych danych.
     *
     * <p>Metoda jest uzywana jako zabezpieczenie przy tworzeniu pierwszej
     * organizacji. Jezeli frontend wczesniej wywolal {@code PUT /api/profile},
     * zapisana tam nazwa pozostaje bez zmian. Podana nazwa jest uzywana tylko
     * podczas tworzenia brakujacego rekordu.</p>
     *
     * @param userId UUID uzytkownika z Supabase Auth
     * @param displayName nazwa uzywana wylacznie dla nowego profilu
     * @return istniejacy albo wlasnie utworzony profil
     */
    @Transactional
    public ProfileEntity ensureProfileExists(UUID userId, String displayName) {
        OffsetDateTime now = OffsetDateTime.now();
        return jdbcTemplate.query("""
                        insert into platform.profiles(user_id, display_name, created_at, updated_at)
                        values (?, ?, ?, ?)
                        on conflict (user_id) do nothing
                        returning id, user_id, display_name, created_at, updated_at
                        """,
                PROFILE_ROW_MAPPER,
                userId,
                displayName,
                now,
                now
        ).stream().findFirst()
                .or(() -> profileRepository.findByUserId(userId))
                .orElseThrow();
    }
}

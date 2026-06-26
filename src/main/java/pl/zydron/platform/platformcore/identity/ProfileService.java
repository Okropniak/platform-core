package pl.zydron.platform.platformcore.identity;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
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
     * Wyszukuje profil powiązany z użytkownikiem Supabase.
     *
     * @param userId UUID z pola {@code sub} tokenu
     * @return profil lub pusty wynik, jeżeli profil nie został jeszcze utworzony
     */
    @Transactional(readOnly = true)
    public Optional<ProfileEntity> findByUserId(UUID userId) {
        return profileRepository.findByUserId(userId);
    }

    /**
     * Tworzy profil albo aktualizuje jego nazwę, gdy profil już istnieje.
     *
     * <p>Metoda używa jednego atomowego polecenia PostgreSQL
     * {@code INSERT ... ON CONFLICT DO UPDATE}. Dzięki temu dwie równoległe
     * prośby tego samego użytkownika nie trafiają w opóźniony flush Hibernate
     * i nie kończą się błędem 500 przy naruszeniu unikalnego {@code user_id}.</p>
     */
    @Transactional
    public ProfileEntity upsertProfile(UUID userId, String displayName) {
        OffsetDateTime now = OffsetDateTime.now();
        return queryProfile("""
                        insert into platform.profiles(user_id, display_name, created_at, updated_at)
                        values (?, ?, ?, ?)
                        on conflict (user_id) do update
                            set display_name = excluded.display_name,
                                updated_at = excluded.updated_at
                        returning id, user_id, display_name, created_at, updated_at
                        """,
                userId,
                displayName,
                now,
                now
        ).orElseThrow(() -> new IllegalStateException("upsertProfile returned no row for userId: " + userId));
    }

    /**
     * Zapewnia, że użytkownik ma profil, ale nie zmienia istniejących danych.
     *
     * <p>Metoda jest używana jako zabezpieczenie przy tworzeniu pierwszej
     * organizacji. Jeżeli frontend wcześniej wywołał {@code PUT /api/profile},
     * zapisana tam nazwa pozostaje bez zmian. Podana nazwa jest używana tylko
     * podczas tworzenia brakującego rekordu.</p>
     *
     * @param userId UUID użytkownika z Supabase Auth
     * @param displayName nazwa używana wyłącznie dla nowego profilu
     * @return istniejący albo właśnie utworzony profil
     */
    @Transactional
    public ProfileEntity ensureProfileExists(UUID userId, String displayName) {
        OffsetDateTime now = OffsetDateTime.now();
        return queryProfile("""
                        insert into platform.profiles(user_id, display_name, created_at, updated_at)
                        values (?, ?, ?, ?)
                        on conflict (user_id) do nothing
                        returning id, user_id, display_name, created_at, updated_at
                        """,
                userId,
                displayName,
                now,
                now
        ).or(() -> findByUserIdJdbc(userId))
                .orElseThrow(() -> new IllegalStateException(
                        "Profile not found after ensureProfileExists for userId: " + userId
                ));
    }

    private Optional<ProfileEntity> findByUserIdJdbc(UUID userId) {
        return queryProfile("""
                        select id, user_id, display_name, created_at, updated_at
                        from platform.profiles
                        where user_id = ?
                        """,
                userId
        );
    }

    private Optional<ProfileEntity> queryProfile(String sql, Object... args) {
        return jdbcTemplate.query(sql, new ArgumentPreparedStatementSetter(args), PROFILE_ROW_MAPPER)
                .stream()
                .findFirst();
    }
}

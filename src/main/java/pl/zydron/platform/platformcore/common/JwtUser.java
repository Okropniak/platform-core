package pl.zydron.platform.platformcore.common;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Pomocnicza klasa odczytująca identyfikator użytkownika z JWT.
 *
 * <p>Supabase zapisuje UUID użytkownika w standardowym polu {@code sub}.
 * Serwisy otrzymują jawny UUID, dzięki czemu nie muszą znać struktury tokenu.</p>
 */
public final class JwtUser {

    private JwtUser() {
    }

    /**
     * Zamienia pole {@code sub} tokenu na UUID użytkownika.
     *
     * @param jwt poprawnie zweryfikowany token przekazany przez Spring Security
     * @return UUID zalogowanego użytkownika
     * @throws InvalidJwtSubjectException gdy {@code sub} nie jest UUID
     */
    public static UUID userId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new InvalidJwtSubjectException("JWT subject must be a UUID.", exception);
        }
    }
}

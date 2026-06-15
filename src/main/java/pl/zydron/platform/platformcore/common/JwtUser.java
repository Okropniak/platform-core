package pl.zydron.platform.platformcore.common;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public final class JwtUser {

    private JwtUser() {
    }

    public static UUID userId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new InvalidJwtSubjectException("JWT subject must be a UUID.", exception);
        }
    }
}

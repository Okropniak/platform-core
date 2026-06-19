package pl.zydron.platform.platformcore.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        var scopesConverter = new JwtGrantedAuthoritiesConverter();
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var authorities = new ArrayList<>(scopesConverter.convert(jwt));
            collectRoles(jwt.getClaim("roles"), authorities);
            collectRoles(jwt.getClaim("role"), authorities);
            collectAppMetadataRoles(jwt.getClaim("app_metadata"), authorities);
            collectAppMetadataRoles(jwt.getClaim("raw_app_meta_data"), authorities);
            return authorities;
        });
        return converter;
    }

    private static void collectAppMetadataRoles(Object claim, Collection<GrantedAuthority> authorities) {
        if (claim instanceof Map<?, ?> metadata) {
            collectRoles(metadata.get("roles"), authorities);
            collectRoles(metadata.get("role"), authorities);
        }
    }

    private static void collectRoles(Object claim, Collection<GrantedAuthority> authorities) {
        if (claim instanceof Collection<?> roles) {
            roles.forEach(role -> addRole(String.valueOf(role), authorities));
        } else if (claim instanceof String role) {
            for (String item : role.split("[, ]+")) {
                addRole(item, authorities);
            }
        }
    }

    private static void addRole(String role, Collection<GrantedAuthority> authorities) {
        if (role == null || role.isBlank()) {
            return;
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("ROLE_")) {
            authorities.add(new SimpleGrantedAuthority(normalized));
        } else {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + normalized));
        }
    }
}

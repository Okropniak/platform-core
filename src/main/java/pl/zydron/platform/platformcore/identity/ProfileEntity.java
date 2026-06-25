package pl.zydron.platform.platformcore.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(schema = "platform", name = "profiles")
/**
 * Encja danych aplikacyjnych użytkownika.
 *
 * <p>{@code userId} wskazuje rekord Supabase {@code auth.users}. Osobny
 * techniczny identyfikator {@code id} pozwala rozwijać profil bez zmieniania
 * tożsamości dostarczanej przez zewnętrzny system uwierzytelnienia.</p>
 */
public class ProfileEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID userId;

    private String displayName;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;
}

package pl.zydron.platform.platformcore.tenants;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
/**
 * Złożony identyfikator członkostwa w organizacji.
 *
 * <p>Ta sama osoba może należeć do wielu organizacji, ale w jednej organizacji
 * może mieć tylko jeden rekord członkostwa. Dlatego klucz składa się z UUID
 * organizacji i UUID użytkownika.</p>
 */
public class OrganizationMemberId implements Serializable {

    private UUID organizationId;

    private UUID userId;
}

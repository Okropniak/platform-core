package pl.zydron.platform.platformcore.tenants;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(schema = "platform", name = "organization_members")
/**
 * Encja łącząca użytkownika z organizacją.
 *
 * <p>{@link EmbeddedId} przechowuje oba identyfikatory klucza. Rola określa
 * zakres uprawnień, a status pozwala wyłączyć członkostwo bez usuwania jego
 * historii.</p>
 */
public class OrganizationMemberEntity {

    @EmbeddedId
    private OrganizationMemberId id;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private OffsetDateTime createdAt;
}

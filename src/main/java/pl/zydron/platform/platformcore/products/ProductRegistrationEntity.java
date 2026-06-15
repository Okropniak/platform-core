package pl.zydron.platform.platformcore.products;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(
        schema = "platform",
        name = "product_registrations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "user_id", "product_code"})
)
public class ProductRegistrationEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String productCode;

    private String acceptedTermsVersion;

    private String acceptedPrivacyVersion;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private OffsetDateTime registeredAt;
}

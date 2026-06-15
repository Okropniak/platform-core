package pl.zydron.platform.platformcore.entitlements;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(schema = "entitlement", name = "features")
public class FeatureEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String productCode;

    @Column(nullable = false)
    private String featureCode;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private boolean active;
}

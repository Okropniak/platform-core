package pl.zydron.platform.platformcore.products;

import jakarta.persistence.Column;
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
public class ProductAccessId implements Serializable {

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String productCode;
}

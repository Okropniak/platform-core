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
/**
 * Złożony klucz rekordu dostępu do produktu.
 *
 * <p>Dostęp jest jednoznacznie określony przez organizację, użytkownika
 * i produkt. {@code @Embeddable} pozwala użyć tych trzech pól jako jednego
 * identyfikatora encji JPA.</p>
 */
public class ProductAccessId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String productCode;
}

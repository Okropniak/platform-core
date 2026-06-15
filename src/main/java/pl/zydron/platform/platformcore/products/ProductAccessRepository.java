package pl.zydron.platform.platformcore.products;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductAccessRepository extends JpaRepository<ProductAccessEntity, ProductAccessId> {
}

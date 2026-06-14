package pl.zydron.platform.platformcore.tenants;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMemberEntity, OrganizationMemberId> {

    Optional<OrganizationMemberEntity> findByIdOrganizationIdAndIdUserId(UUID organizationId, UUID userId);

    @Query("""
            select o
            from OrganizationEntity o
            join OrganizationMemberEntity m on m.id.organizationId = o.id
            where m.id.userId = :userId and m.status = 'active'
            order by o.createdAt desc
            """)
    List<OrganizationEntity> findActiveOrganizationsForUser(UUID userId);
}

package pl.zydron.platform.platformcore.tenants;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.zydron.platform.platformcore.common.BadRequestException;
import pl.zydron.platform.platformcore.common.ConflictException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantService {

    private static final Set<String> MANAGER_ROLES = Set.of("owner", "admin");

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public OrganizationEntity createOrganization(UUID userId, String name, String type) {
        var now = OffsetDateTime.now();
        var organization = organizationRepository.save(OrganizationEntity.builder()
                .name(name)
                .type(type)
                .createdBy(userId)
                .createdAt(now)
                .updatedAt(now)
                .build());

        organizationMemberRepository.save(OrganizationMemberEntity.builder()
                .id(new OrganizationMemberId(organization.getId(), userId))
                .role("owner")
                .status("active")
                .createdAt(now)
                .build());

        return organization;
    }

    @Transactional
    public OrganizationMemberEntity addMember(UUID organizationId, UUID requestingUserId, UUID targetUserId, String role) {
        requireManager(organizationId, requestingUserId);
        requireExistingUser(targetUserId);

        if (organizationMemberRepository.findByIdOrganizationIdAndIdUserId(organizationId, targetUserId).isPresent()) {
            throw new ConflictException("User is already an organization member.");
        }

        return organizationMemberRepository.save(OrganizationMemberEntity.builder()
                .id(new OrganizationMemberId(organizationId, targetUserId))
                .role(role)
                .status("active")
                .createdAt(OffsetDateTime.now())
                .build());
    }

    @Transactional(readOnly = true)
    public List<OrganizationEntity> getOrganizationsForUser(UUID userId) {
        return organizationMemberRepository.findActiveOrganizationsForUser(userId);
    }

    @Transactional(readOnly = true)
    public void requireManager(UUID organizationId, UUID userId) {
        var member = organizationMemberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId)
                .filter(candidate -> "active".equals(candidate.getStatus()))
                .orElseThrow(() -> new TenantAccessDeniedException("User is not an active organization member."));

        if (!MANAGER_ROLES.contains(member.getRole())) {
            throw new TenantAccessDeniedException("User is not allowed to manage this organization.");
        }
    }

    private void requireExistingUser(UUID userId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "select exists (select 1 from auth.users where id = ?)",
                Boolean.class,
                userId
        );

        if (!Boolean.TRUE.equals(exists)) {
            throw new BadRequestException("Target user does not exist.");
        }
    }
}

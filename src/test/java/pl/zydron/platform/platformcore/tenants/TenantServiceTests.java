package pl.zydron.platform.platformcore.tenants;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.zydron.platform.platformcore.common.BadRequestException;
import pl.zydron.platform.platformcore.common.ConflictException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantServiceTests {

    private final OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
    private final OrganizationMemberRepository organizationMemberRepository = mock(OrganizationMemberRepository.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final TenantService tenantService = new TenantService(
            organizationRepository,
            organizationMemberRepository,
            jdbcTemplate
    );

    @Test
    void addMemberRejectsExistingMembershipInsteadOfOverwritingRole() {
        UUID organizationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        when(organizationMemberRepository.findByIdOrganizationIdAndIdUserId(organizationId, adminId))
                .thenReturn(Optional.of(member(organizationId, adminId, "admin")));
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq(targetId)))
                .thenReturn(true);
        when(organizationMemberRepository.findByIdOrganizationIdAndIdUserId(organizationId, targetId))
                .thenReturn(Optional.of(member(organizationId, targetId, "owner")));

        assertThatThrownBy(() -> tenantService.addMember(organizationId, adminId, targetId, "member"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already an organization member");

        verify(organizationMemberRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void addMemberRejectsUnknownTargetUserBeforeForeignKeyViolation() {
        UUID organizationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        when(organizationMemberRepository.findByIdOrganizationIdAndIdUserId(organizationId, adminId))
                .thenReturn(Optional.of(member(organizationId, adminId, "admin")));
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq(targetId)))
                .thenReturn(false);

        assertThatThrownBy(() -> tenantService.addMember(organizationId, adminId, targetId, "member"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Target user does not exist");

        verify(organizationMemberRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private OrganizationMemberEntity member(UUID organizationId, UUID userId, String role) {
        return OrganizationMemberEntity.builder()
                .id(new OrganizationMemberId(organizationId, userId))
                .role(role)
                .status("active")
                .createdAt(OffsetDateTime.now())
                .build();
    }
}

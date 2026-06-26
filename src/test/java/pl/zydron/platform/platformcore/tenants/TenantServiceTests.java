package pl.zydron.platform.platformcore.tenants;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.zydron.platform.platformcore.common.BadRequestException;
import pl.zydron.platform.platformcore.common.ConflictException;
import pl.zydron.platform.platformcore.identity.ProfileService;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantServiceTests {

    private final OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
    private final OrganizationMemberRepository organizationMemberRepository = mock(OrganizationMemberRepository.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ProfileService profileService = mock(ProfileService.class);
    private final TenantService tenantService = new TenantService(
            organizationRepository,
            organizationMemberRepository,
            jdbcTemplate,
            profileService
    );

    @Test
    void createOrganizationEnsuresProfileBeforeSavingTenant() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        OrganizationEntity organization = OrganizationEntity.builder()
                .id(organizationId)
                .name("Acme")
                .type("company")
                .createdBy(userId)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        when(organizationRepository.save(any(OrganizationEntity.class))).thenReturn(organization);
        when(organizationMemberRepository.save(any(OrganizationMemberEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrganizationEntity result = tenantService.createOrganization(
                userId,
                "Jan Kowalski",
                "Acme",
                "company"
        );

        assertThat(result).isSameAs(organization);
        var inOrder = inOrder(profileService, organizationRepository);
        inOrder.verify(profileService).ensureProfileExists(userId, "Jan Kowalski");
        inOrder.verify(organizationRepository).save(any(OrganizationEntity.class));
        verify(organizationMemberRepository).save(any(OrganizationMemberEntity.class));
    }

    @Test
    void createOrganizationDoesNotSaveTenantWhenProfileBootstrapFails() {
        UUID userId = UUID.randomUUID();
        doThrow(new IllegalStateException("profile unavailable"))
                .when(profileService).ensureProfileExists(userId, "Jan Kowalski");

        assertThatThrownBy(() -> tenantService.createOrganization(
                userId,
                "Jan Kowalski",
                "Acme",
                "company"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("profile unavailable");

        verify(organizationRepository, never()).save(any(OrganizationEntity.class));
        verify(organizationMemberRepository, never()).save(any(OrganizationMemberEntity.class));
    }

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

    @Test
    void requireOrganizationExistsAcceptsKnownOrganization() {
        UUID organizationId = UUID.randomUUID();
        when(organizationRepository.existsById(organizationId)).thenReturn(true);

        tenantService.requireOrganizationExists(organizationId);

        verify(organizationRepository).existsById(organizationId);
    }

    @Test
    void requireOrganizationExistsRejectsUnknownOrganization() {
        UUID organizationId = UUID.randomUUID();
        when(organizationRepository.existsById(organizationId)).thenReturn(false);

        assertThatThrownBy(() -> tenantService.requireOrganizationExists(organizationId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Organization does not exist");
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

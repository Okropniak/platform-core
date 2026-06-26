package pl.zydron.platform.platformcore.tenants;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.zydron.platform.platformcore.common.BadRequestException;
import pl.zydron.platform.platformcore.common.ConflictException;
import pl.zydron.platform.platformcore.common.PlatformAccessDeniedException;
import pl.zydron.platform.platformcore.identity.ProfileService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Implementuje reguły organizacji i członkostw.
 *
 * <p>Serwis jest publicznym punktem wejścia do modułu tenants. Inne moduły
 * używają go między innymi do sprawdzania, czy użytkownik jest aktywnym
 * członkiem albo managerem organizacji.</p>
 */
@Service
@RequiredArgsConstructor
public class TenantService {

    private static final Set<String> MANAGER_ROLES = Set.of("owner", "admin");

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ProfileService profileService;

    @Transactional
    /**
     * Tworzy organizację i członkostwo właściciela w jednej transakcji.
     *
     * <p>Jeżeli drugi zapis się nie powiedzie, transakcja wycofa również
     * utworzenie organizacji, dzięki czemu nie powstanie organizacja bez
     * właściciela.</p>
     */
    public OrganizationEntity createOrganization(UUID userId, String displayName, String name, String type) {
        profileService.ensureProfileExists(userId, displayName);

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
    /**
     * Dodaje istniejącego użytkownika Supabase do organizacji.
     *
     * @throws PlatformAccessDeniedException gdy wywołujący nie jest managerem
     * @throws BadRequestException gdy użytkownik docelowy nie istnieje
     * @throws ConflictException gdy członkostwo już istnieje
     */
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
    /**
     * Zwraca organizacje z aktywnym członkostwem wskazanego użytkownika.
     */
    public List<OrganizationEntity> getOrganizationsForUser(UUID userId) {
        return organizationMemberRepository.findActiveOrganizationsForUser(userId);
    }

    @Transactional(readOnly = true)
    /**
     * Sprawdza, czy użytkownik ma aktywną rolę owner lub admin.
     *
     * <p>Metoda nie zwraca wartości. Brak wyjątku oznacza pozytywny wynik
     * autoryzacji.</p>
     */
    public void requireManager(UUID organizationId, UUID userId) {
        var member = requireActiveMember(organizationId, userId);

        if (!MANAGER_ROLES.contains(member.getRole())) {
            throw new PlatformAccessDeniedException("User is not allowed to manage this organization.");
        }
    }

    @Transactional(readOnly = true)
    /**
     * Zwraca aktywne członkostwo albo przerywa operację błędem HTTP 403.
     */
    public OrganizationMemberEntity requireActiveMember(UUID organizationId, UUID userId) {
        var member = organizationMemberRepository.findByIdOrganizationIdAndIdUserId(organizationId, userId)
                .filter(candidate -> "active".equals(candidate.getStatus()))
                .orElseThrow(() -> new PlatformAccessDeniedException("User is not an active organization member."));

        return member;
    }

    /**
     * Sprawdza istnienie organizacji bez wymagania członkostwa użytkownika.
     *
     * <p>Metoda jest publicznym kontraktem modułu tenants dla operacji
     * administracyjnych wykonywanych przez inne moduły. Dzięki temu billing
     * nie musi korzystać bezpośrednio z repozytorium organizacji.</p>
     *
     * @throws BadRequestException gdy organizacja nie istnieje
     */
    @Transactional(readOnly = true)
    public void requireOrganizationExists(UUID organizationId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new BadRequestException("Organization does not exist.");
        }
    }

    private void requireExistingUser(UUID userId) {
        // Repozytoria aplikacji nie mapują tabeli auth.users, dlatego ten prosty
        // test istnienia wykonujemy bezpośrednio przez JdbcTemplate.
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

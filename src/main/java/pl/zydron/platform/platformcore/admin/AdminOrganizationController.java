package pl.zydron.platform.platformcore.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/organizations")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
/**
 * Udostępnia administratorowi listę i szczegóły wszystkich organizacji.
 */
public class AdminOrganizationController {

    private final AdminReadService adminReadService;

    @GetMapping
    /**
     * Zwraca stronę organizacji zgodnie z parametrami Spring Data Pageable.
     */
    Page<AdminReadService.OrganizationSummary> organizations(Pageable pageable) {
        return adminReadService.organizations(pageable);
    }

    @GetMapping("/{id}")
    /**
     * Zwraca organizację wraz z liczbą członków, subskrypcjami i entitlementami.
     */
    AdminReadService.OrganizationDetail organization(@PathVariable UUID id) {
        return adminReadService.organizationDetail(id);
    }
}

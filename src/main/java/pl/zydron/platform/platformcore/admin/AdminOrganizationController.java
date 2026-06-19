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
public class AdminOrganizationController {

    private final AdminReadService adminReadService;

    @GetMapping
    Page<AdminReadService.OrganizationSummary> organizations(Pageable pageable) {
        return adminReadService.organizations(pageable);
    }

    @GetMapping("/{id}")
    AdminReadService.OrganizationDetail organization(@PathVariable UUID id) {
        return adminReadService.organizationDetail(id);
    }
}

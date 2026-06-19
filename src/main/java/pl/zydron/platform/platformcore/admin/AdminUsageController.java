package pl.zydron.platform.platformcore.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/organizations/{id}/usage")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUsageController {

    private final AdminReadService adminReadService;

    @GetMapping
    List<AdminReadService.UsageCounterRow> usage(
            @PathVariable UUID id,
            @RequestParam(required = false) String productCode
    ) {
        return adminReadService.usage(id, productCode);
    }
}

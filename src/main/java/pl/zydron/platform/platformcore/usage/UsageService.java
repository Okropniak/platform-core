package pl.zydron.platform.platformcore.usage;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.zydron.platform.platformcore.audit.AuditService;
import pl.zydron.platform.platformcore.common.BadRequestException;
import pl.zydron.platform.platformcore.tenants.TenantService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Łączy autoryzację organizacji z funkcjami PostgreSQL naliczającymi użycie.
 *
 * <p>Wrażliwa logika współbieżności, idempotencji i atomowej aktualizacji
 * liczników znajduje się w funkcjach {@code usage.*}. Java przekazuje
 * parametry, parsuje wynik JSON i uruchamia audit odrzuconych operacji.</p>
 */
@Service
@RequiredArgsConstructor
public class UsageService {

    private final UsageCounterRepository usageCounterRepository;
    private final UsageReservationRepository usageReservationRepository;
    private final TenantService tenantService;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    /**
     * Natychmiast zwiększa licznik, jeśli limit na to pozwala.
     *
     * @param idempotencyKey unikalny klucz logicznej operacji; ponowienie
     *                       z tym samym kluczem zwraca poprzedni wynik
     */
    public UsageResult consumeUsage(
            UUID organizationId,
            UUID userId,
            String productCode,
            String metricCode,
            BigDecimal amount,
            String idempotencyKey
    ) {
        tenantService.requireActiveMember(organizationId, userId);
        requireIdempotencyKey(idempotencyKey);

        UsageResult result = readResult(jdbcTemplate.queryForObject(
                "select usage.consume_usage(?, ?, ?, ?, ?, ?)",
                String.class,
                organizationId,
                userId,
                productCode,
                metricCode,
                amount,
                idempotencyKey
        ), UsageResult.class);
        auditUsageLimitExceeded(organizationId, userId, productCode, metricCode, result.accepted(), result.reason());
        return result;
    }

    @Transactional
    /**
     * Rezerwuje część limitu do późniejszej finalizacji.
     */
    public ReservationResult reserveUsage(
            UUID organizationId,
            UUID userId,
            String productCode,
            String metricCode,
            BigDecimal amount,
            String idempotencyKey
    ) {
        tenantService.requireActiveMember(organizationId, userId);
        requireIdempotencyKey(idempotencyKey);

        ReservationResult result = readResult(jdbcTemplate.queryForObject(
                "select usage.reserve_usage(?, ?, ?, ?, ?, ?)",
                String.class,
                organizationId,
                userId,
                productCode,
                metricCode,
                amount,
                idempotencyKey
        ), ReservationResult.class);
        auditUsageLimitExceeded(organizationId, userId, productCode, metricCode, result.accepted(), result.reason());
        return result;
    }

    @Transactional
    /**
     * Finalizuje rezerwację należącą do zalogowanego użytkownika.
     *
     * <p>Wyszukiwanie po parze reservationId-userId nie ujawnia, czy podany
     * identyfikator należy do innego użytkownika. W obu przypadkach klient
     * otrzymuje {@code reservation_not_found}.</p>
     */
    public FinalizationResult finalizeUsage(UUID reservationId, UUID userId, BigDecimal actualAmount) {
        var reservation = usageReservationRepository.findByIdAndUserId(reservationId, userId);
        if (reservation.isEmpty()) {
            return new FinalizationResult(false, "reservation_not_found", reservationId, null, null, null, null);
        }
        tenantService.requireActiveMember(reservation.get().getOrganizationId(), userId);

        FinalizationResult result = readResult(jdbcTemplate.queryForObject(
                "select usage.finalize_usage(?, ?, ?)",
                String.class,
                reservationId,
                userId,
                actualAmount
        ), FinalizationResult.class);
        // Powtórna finalizacja nie jest audytowana: reservation_not_active może
        // być zwykłym, bezpiecznym retry klienta. Pozostałe odrzucenia są ważne.
        if (!result.finalized() && !"reservation_not_active".equals(result.reason())) {
            String eventType = "limit_exceeded".equals(result.reason())
                    ? "usage_limit_exceeded"
                    : "usage_operation_rejected";
            auditService.record(
                    reservation.get().getOrganizationId(),
                    userId,
                    reservation.get().getProductCode(),
                    eventType,
                    "usage_reservation",
                    reservationId.toString(),
                    Map.of(
                            "metricCode", reservation.get().getMetricCode(),
                            "operation", "finalize",
                            "reason", result.reason()
                    )
            );
        }
        return result;
    }

    @Transactional(readOnly = true)
    /**
     * Zwraca organizacyjne liczniki użycia dla produktu.
     */
    public List<UsageSummaryItem> getUsageSummary(UUID organizationId, UUID userId, String productCode) {
        tenantService.requireActiveMember(organizationId, userId);
        return usageCounterRepository.findOrganizationSummary(organizationId, productCode).stream()
                .map(counter -> new UsageSummaryItem(
                        counter.getMetricCode(),
                        counter.getPeriodStart(),
                        counter.getPeriodEnd(),
                        counter.getUsedValue(),
                        counter.getReservedValue()
                ))
                .toList();
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency key is required.");
        }
    }

    private <T> T readResult(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JacksonException exception) {
            // Zmiana formatu JSON w funkcji SQL wymaga jednoczesnej zmiany
            // rekordu Java. Niespójność jest błędem kontraktu wewnętrznego.
            throw new IllegalStateException("Invalid usage result returned by database.", exception);
        }
    }

    private void auditUsageLimitExceeded(
            UUID organizationId,
            UUID userId,
            String productCode,
            String metricCode,
            boolean accepted,
            String reason
    ) {
        if (!accepted && "limit_exceeded".equals(reason)) {
            auditService.record(
                    organizationId,
                    userId,
                    productCode,
                    "usage_limit_exceeded",
                    "usage_metric",
                    metricCode,
                    Map.of("metricCode", metricCode)
            );
        }
    }
}

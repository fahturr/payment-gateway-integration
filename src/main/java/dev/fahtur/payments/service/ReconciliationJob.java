package dev.fahtur.payments.service;

import com.fasterxml.jackson.databind.JsonNode;
import dev.fahtur.payments.domain.Payment;
import dev.fahtur.payments.domain.PaymentStatus;
import dev.fahtur.payments.midtrans.MidtransClient;
import dev.fahtur.payments.midtrans.MidtransStatusMapper;
import dev.fahtur.payments.repo.PaymentRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Webhooks can be lost (downtime, network, misconfigured URL). This job asks Midtrans for the
 * truth about payments that have been pending for too long and applies the same state machine.
 * Running several instances is harmless (updates are idempotent + optimistic-locked); add ShedLock
 * if you want to avoid duplicate API calls.
 */
@Component
@ConditionalOnProperty(name = "reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

    private final PaymentRepository payments;
    private final MidtransClient midtrans;
    private final TransactionTemplate tx;
    private final long pendingOlderThanMinutes;
    private final long orphanAfterMinutes;
    private final int batchSize;

    public ReconciliationJob(PaymentRepository payments, MidtransClient midtrans, PlatformTransactionManager tm,
                             @Value("${reconciliation.pending-older-than-minutes:5}") long pendingOlderThanMinutes,
                             @Value("${reconciliation.orphan-after-minutes:60}") long orphanAfterMinutes,
                             @Value("${reconciliation.batch-size:50}") int batchSize) {
        this.payments = payments;
        this.midtrans = midtrans;
        this.tx = new TransactionTemplate(tm);
        this.pendingOlderThanMinutes = pendingOlderThanMinutes;
        this.orphanAfterMinutes = orphanAfterMinutes;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${reconciliation.interval-ms:60000}")
    public void run() {
        int updated = reconcileBatch();
        if (updated > 0) {
            log.info("Reconciliation updated {} payment(s)", updated);
        }
    }

    public int reconcileBatch() {
        Instant cutoff = Instant.now().minus(pendingOlderThanMinutes, ChronoUnit.MINUTES);
        List<Payment> candidates = payments.findByStatusInAndCreatedAtBefore(
                List.of(PaymentStatus.PENDING, PaymentStatus.CHALLENGE), cutoff,
                PageRequest.of(0, batchSize, Sort.by("createdAt")));

        int updated = 0;
        for (Payment p : candidates) {
            try {
                if (reconcile(p)) {
                    updated++;
                }
            } catch (RuntimeException e) {
                // One bad order must not block the rest of the batch.
                log.warn("Could not reconcile order {}: {}", p.getOrderId(), e.getMessage());
            }
        }
        return updated;
    }

    private boolean reconcile(Payment p) {
        JsonNode status = midtrans.getTransactionStatus(p.getOrderId());

        if ("404".equals(status.path("status_code").asText(""))) {
            // Midtrans has never heard of this order: our create call died before reaching it.
            boolean orphaned = p.getCreatedAt().isBefore(Instant.now().minus(orphanAfterMinutes, ChronoUnit.MINUTES));
            if (!orphaned) {
                return false;
            }
            return Boolean.TRUE.equals(tx.execute(s -> {
                Payment fresh = payments.findById(p.getId()).orElseThrow();
                boolean wasPending = fresh.getStatus() == PaymentStatus.PENDING || fresh.getStatus() == PaymentStatus.CHALLENGE;
                fresh.markFailed("order_not_found_at_provider");
                return wasPending;
            }));
        }

        Optional<PaymentStatus> target = MidtransStatusMapper.map(
                status.path("transaction_status").asText(""), status.path("fraud_status").asText(""));
        if (target.isEmpty()) {
            return false;
        }
        return Boolean.TRUE.equals(tx.execute(s ->
                payments.findById(p.getId()).orElseThrow().transitionTo(target.get())));
    }
}

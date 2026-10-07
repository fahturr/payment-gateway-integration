package dev.fahtur.payments.service;

import dev.fahtur.payments.domain.Payment;
import dev.fahtur.payments.error.IdempotencyConflictException;
import dev.fahtur.payments.error.PaymentNotFoundException;
import dev.fahtur.payments.error.PaymentProviderException;
import dev.fahtur.payments.midtrans.MidtransClient;
import dev.fahtur.payments.midtrans.MidtransClient.SnapTransaction;
import dev.fahtur.payments.midtrans.MidtransException;
import dev.fahtur.payments.repo.PaymentRepository;
import dev.fahtur.payments.util.Hashing;
import dev.fahtur.payments.web.CreatePaymentRequest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creating a payment is a three-step flow, deliberately NOT one big transaction:
 * <ol>
 *   <li>reserve: insert the payment row (unique Idempotency-Key) and commit;</li>
 *   <li>call Midtrans over the network (no DB transaction or connection held open);</li>
 *   <li>store the Snap token, or mark the payment FAILED if the provider is unreachable.</li>
 * </ol>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    public record CreateResult(Payment payment, boolean replayed) {
    }

    private record Reservation(Payment payment, boolean created) {
    }

    private final PaymentRepository payments;
    private final MidtransClient midtrans;
    private final TransactionTemplate tx;

    public PaymentService(PaymentRepository payments, MidtransClient midtrans, PlatformTransactionManager tm) {
        this.payments = payments;
        this.midtrans = midtrans;
        this.tx = new TransactionTemplate(tm);
    }

    public CreateResult create(String idempotencyKey, CreatePaymentRequest req) {
        String hash = Hashing.sha256Hex(
                req.amount() + "|" + req.customerName().trim() + "|" + req.customerEmail().trim().toLowerCase(Locale.ROOT));

        Reservation reservation;
        try {
            reservation = Objects.requireNonNull(tx.execute(s -> reserve(idempotencyKey, hash, req)));
        } catch (DataIntegrityViolationException raced) {
            // Two requests with the same key raced; the loser re-reads the winner's row.
            reservation = Objects.requireNonNull(tx.execute(s -> reserve(idempotencyKey, hash, req)));
        }

        if (!reservation.created()) {
            return new CreateResult(reservation.payment(), true);
        }

        Payment reserved = reservation.payment();
        try {
            SnapTransaction snap = midtrans.createSnapTransaction(
                    reserved.getOrderId(), reserved.getAmount(), reserved.getCustomerName(), reserved.getCustomerEmail());
            Payment updated = Objects.requireNonNull(tx.execute(s -> {
                Payment p = payments.findById(reserved.getId()).orElseThrow();
                p.attachSnap(snap.token(), snap.redirectUrl());
                return payments.save(p);
            }));
            return new CreateResult(updated, false);
        } catch (MidtransException e) {
            log.error("Midtrans unavailable for order {}: {}", reserved.getOrderId(), e.getMessage());
            tx.executeWithoutResult(s -> payments.findById(reserved.getId())
                    .ifPresent(p -> p.markFailed("provider_unavailable")));
            throw new PaymentProviderException("Payment provider is unavailable, please retry with a new Idempotency-Key", e);
        }
    }

    public Payment get(String orderId) {
        return payments.findByOrderId(orderId).orElseThrow(PaymentNotFoundException::new);
    }

    private Reservation reserve(String key, String hash, CreatePaymentRequest req) {
        Optional<Payment> existing = payments.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(hash)) {
                throw new IdempotencyConflictException();
            }
            return new Reservation(existing.get(), false);
        }
        Payment created = Payment.create(newOrderId(), key, hash, req.amount(),
                req.customerName().trim(), req.customerEmail().trim());
        return new Reservation(payments.saveAndFlush(created), true);
    }

    private static String newOrderId() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "INV-" + DAY.format(LocalDate.now(ZoneOffset.UTC)) + "-" + suffix;
    }
}

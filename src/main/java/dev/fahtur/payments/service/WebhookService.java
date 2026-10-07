package dev.fahtur.payments.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fahtur.payments.domain.Payment;
import dev.fahtur.payments.domain.PaymentStatus;
import dev.fahtur.payments.domain.WebhookEvent;
import dev.fahtur.payments.error.BadRequestException;
import dev.fahtur.payments.error.InvalidSignatureException;
import dev.fahtur.payments.midtrans.MidtransStatusMapper;
import dev.fahtur.payments.midtrans.SignatureVerifier;
import dev.fahtur.payments.repo.PaymentRepository;
import dev.fahtur.payments.repo.WebhookEventRepository;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    public enum Outcome {
        APPLIED, IGNORED_TRANSITION, UNKNOWN_STATUS, DUPLICATE, UNKNOWN_ORDER, AMOUNT_MISMATCH
    }

    private final ObjectMapper mapper;
    private final SignatureVerifier verifier;
    private final PaymentRepository payments;
    private final WebhookEventRepository events;
    private final TransactionTemplate tx;

    public WebhookService(ObjectMapper mapper, SignatureVerifier verifier, PaymentRepository payments,
                          WebhookEventRepository events, PlatformTransactionManager tm) {
        this.mapper = mapper;
        this.verifier = verifier;
        this.payments = payments;
        this.events = events;
        this.tx = new TransactionTemplate(tm);
    }

    /**
     * Always safe to call repeatedly: signature is verified first, duplicates are detected via a
     * unique event key, and the status change goes through the forward-only state machine.
     */
    public Outcome handle(String rawBody) {
        JsonNode n = parse(rawBody);
        String orderId = text(n, "order_id");
        String statusCode = text(n, "status_code");
        String gross = text(n, "gross_amount");
        String signature = text(n, "signature_key");
        String txStatus = text(n, "transaction_status");
        String fraud = text(n, "fraud_status");
        String txId = text(n, "transaction_id");

        if (!verifier.isValid(orderId, statusCode, gross, signature)) {
            log.warn("Rejected webhook with invalid signature for order '{}'", orderId);
            throw new InvalidSignatureException();
        }
        if (txStatus.isBlank()) {
            throw new BadRequestException("transaction_status is missing");
        }

        Optional<PaymentStatus> target = MidtransStatusMapper.map(txStatus, fraud);
        String eventKey = String.join("|", orderId, txStatus, fraud, txId);

        try {
            return Objects.requireNonNull(tx.execute(s -> process(eventKey, orderId, txStatus, gross, target, rawBody)));
        } catch (DataIntegrityViolationException duplicateDeliveredConcurrently) {
            return Outcome.DUPLICATE;
        }
    }

    private Outcome process(String eventKey, String orderId, String txStatus, String gross,
                            Optional<PaymentStatus> target, String rawBody) {
        if (events.existsByEventKey(eventKey)) {
            return Outcome.DUPLICATE;
        }
        events.saveAndFlush(WebhookEvent.of(eventKey, orderId, txStatus, rawBody));

        Optional<Payment> found = payments.findByOrderId(orderId);
        if (found.isEmpty()) {
            log.warn("Webhook for unknown order {}", orderId);
            return Outcome.UNKNOWN_ORDER;
        }
        Payment payment = found.get();

        if (!amountMatches(payment, gross)) {
            log.error("Amount mismatch for order {}: expected {}, webhook says {}", orderId, payment.getAmount(), gross);
            return Outcome.AMOUNT_MISMATCH;
        }
        if (target.isEmpty()) {
            return Outcome.UNKNOWN_STATUS;
        }
        return payment.transitionTo(target.get()) ? Outcome.APPLIED : Outcome.IGNORED_TRANSITION;
    }

    private static boolean amountMatches(Payment payment, String gross) {
        try {
            return new BigDecimal(gross).compareTo(BigDecimal.valueOf(payment.getAmount())) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private JsonNode parse(String rawBody) {
        try {
            JsonNode node = mapper.readTree(rawBody);
            if (node == null || !node.isObject()) {
                throw new BadRequestException("Webhook body must be a JSON object");
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Webhook body is not valid JSON");
        }
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asText("");
    }
}

package dev.fahtur.payments.midtrans;

import dev.fahtur.payments.domain.PaymentStatus;
import java.util.Optional;

/** Maps Midtrans transaction_status / fraud_status to our own lifecycle. */
public final class MidtransStatusMapper {

    private MidtransStatusMapper() {
    }

    /** Empty means "unknown or ambiguous: do not change anything". */
    public static Optional<PaymentStatus> map(String transactionStatus, String fraudStatus) {
        if (transactionStatus == null) {
            return Optional.empty();
        }
        String fraud = fraudStatus == null ? "" : fraudStatus;
        return switch (transactionStatus) {
            // Card payments: only "accept" counts as paid. Anything else is left alone.
            case "capture" -> switch (fraud) {
                case "accept" -> Optional.of(PaymentStatus.PAID);
                case "challenge" -> Optional.of(PaymentStatus.CHALLENGE);
                default -> Optional.empty();
            };
            case "settlement" -> Optional.of(PaymentStatus.PAID);
            case "pending" -> Optional.of(PaymentStatus.PENDING);
            case "deny", "failure" -> Optional.of(PaymentStatus.FAILED);
            case "cancel" -> Optional.of(PaymentStatus.CANCELED);
            case "expire" -> Optional.of(PaymentStatus.EXPIRED);
            // Simplification: partial refunds are treated as REFUNDED.
            case "refund", "partial_refund" -> Optional.of(PaymentStatus.REFUNDED);
            default -> Optional.empty();
        };
    }
}

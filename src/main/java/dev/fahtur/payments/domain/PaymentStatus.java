package dev.fahtur.payments.domain;

/**
 * Payment lifecycle. Transitions only move "forward": a late or replayed webhook
 * (e.g. "pending" arriving after "settlement") can never regress a payment.
 */
public enum PaymentStatus {
    PENDING, CHALLENGE, PAID, FAILED, CANCELED, EXPIRED, REFUNDED;

    public boolean canTransitionTo(PaymentStatus next) {
        if (this == next) {
            return false;
        }
        return switch (this) {
            case PENDING, CHALLENGE -> true;
            case PAID -> next == REFUNDED;
            case FAILED, CANCELED, EXPIRED, REFUNDED -> false;
        };
    }
}

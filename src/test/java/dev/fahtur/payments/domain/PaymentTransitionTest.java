package dev.fahtur.payments.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PaymentTransitionTest {

    private static Payment newPayment() {
        return Payment.create("INV-1", "key-1", "hash", 10_000, "Budi", "budi@example.com");
    }

    @Test
    void pendingCanBecomePaidThenRefunded() {
        Payment p = newPayment();
        assertTrue(p.transitionTo(PaymentStatus.PAID));
        assertTrue(p.transitionTo(PaymentStatus.REFUNDED));
        assertEquals(PaymentStatus.REFUNDED, p.getStatus());
    }

    @Test
    void lateWebhookCannotRegressAPaidPayment() {
        Payment p = newPayment();
        p.transitionTo(PaymentStatus.PAID);
        assertFalse(p.transitionTo(PaymentStatus.PENDING));
        assertFalse(p.transitionTo(PaymentStatus.EXPIRED));
        assertEquals(PaymentStatus.PAID, p.getStatus());
    }

    @Test
    void terminalStatesAreFinal() {
        for (PaymentStatus terminal : new PaymentStatus[]{
                PaymentStatus.FAILED, PaymentStatus.CANCELED, PaymentStatus.EXPIRED, PaymentStatus.REFUNDED}) {
            Payment p = newPayment();
            p.transitionTo(terminal);
            assertFalse(p.transitionTo(PaymentStatus.PAID), terminal + " must not move to PAID");
        }
    }

    @Test
    void sameStatusIsANoOp() {
        Payment p = newPayment();
        assertFalse(p.transitionTo(PaymentStatus.PENDING));
    }

    @Test
    void markFailedOnlyAppliesWhenStillOpen() {
        Payment open = newPayment();
        open.markFailed("provider_unavailable");
        assertEquals(PaymentStatus.FAILED, open.getStatus());
        assertEquals("provider_unavailable", open.getFailureReason());

        Payment paid = newPayment();
        paid.transitionTo(PaymentStatus.PAID);
        paid.markFailed("should_not_apply");
        assertEquals(PaymentStatus.PAID, paid.getStatus());
        assertNull(paid.getFailureReason());
    }
}

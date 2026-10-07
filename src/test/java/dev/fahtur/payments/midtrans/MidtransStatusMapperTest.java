package dev.fahtur.payments.midtrans;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fahtur.payments.domain.PaymentStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MidtransStatusMapperTest {

    @Test
    void settlementIsPaid() {
        assertEquals(Optional.of(PaymentStatus.PAID), MidtransStatusMapper.map("settlement", ""));
    }

    @Test
    void captureDependsOnFraudStatus() {
        assertEquals(Optional.of(PaymentStatus.PAID), MidtransStatusMapper.map("capture", "accept"));
        assertEquals(Optional.of(PaymentStatus.CHALLENGE), MidtransStatusMapper.map("capture", "challenge"));
        assertTrue(MidtransStatusMapper.map("capture", "deny").isEmpty());
        assertTrue(MidtransStatusMapper.map("capture", null).isEmpty());
    }

    @Test
    void terminalFailureStatuses() {
        assertEquals(Optional.of(PaymentStatus.FAILED), MidtransStatusMapper.map("deny", ""));
        assertEquals(Optional.of(PaymentStatus.FAILED), MidtransStatusMapper.map("failure", ""));
        assertEquals(Optional.of(PaymentStatus.CANCELED), MidtransStatusMapper.map("cancel", ""));
        assertEquals(Optional.of(PaymentStatus.EXPIRED), MidtransStatusMapper.map("expire", ""));
    }

    @Test
    void unknownStatusChangesNothing() {
        assertTrue(MidtransStatusMapper.map("something_new", "").isEmpty());
        assertTrue(MidtransStatusMapper.map(null, "").isEmpty());
    }
}

package dev.fahtur.payments.web;

import dev.fahtur.payments.domain.Payment;
import dev.fahtur.payments.domain.PaymentStatus;
import java.time.Instant;

public record PaymentResponse(
        String orderId,
        PaymentStatus status,
        long amount,
        String currency,
        String snapToken,
        String redirectUrl,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getOrderId(), p.getStatus(), p.getAmount(), p.getCurrency(),
                p.getSnapToken(), p.getRedirectUrl(), p.getCreatedAt(), p.getUpdatedAt());
    }
}

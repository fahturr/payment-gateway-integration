package dev.fahtur.payments.error;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException() {
        super("Payment not found");
    }
}

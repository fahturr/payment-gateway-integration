package dev.fahtur.payments.error;

public class PaymentProviderException extends RuntimeException {
    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}

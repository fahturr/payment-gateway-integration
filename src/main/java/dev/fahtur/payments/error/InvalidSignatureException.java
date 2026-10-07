package dev.fahtur.payments.error;

public class InvalidSignatureException extends RuntimeException {
    public InvalidSignatureException() {
        super("Invalid webhook signature");
    }
}

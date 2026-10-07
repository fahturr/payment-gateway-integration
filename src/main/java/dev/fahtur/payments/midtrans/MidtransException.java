package dev.fahtur.payments.midtrans;

public class MidtransException extends RuntimeException {
    public MidtransException(String message, Throwable cause) {
        super(message, cause);
    }

    public MidtransException(String message) {
        super(message);
    }
}

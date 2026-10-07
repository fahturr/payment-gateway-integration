package dev.fahtur.payments.error;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() {
        super("Idempotency-Key was already used with a different request body");
    }
}

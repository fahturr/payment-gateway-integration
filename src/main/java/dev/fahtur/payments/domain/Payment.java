package dev.fahtur.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private String orderId;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false)
    private String requestHash;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(nullable = false, length = 3)
    private String currency = "IDR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Column(name = "snap_token")
    private String snapToken;

    @Column(name = "redirect_url")
    private String redirectUrl;

    @Column(name = "failure_reason")
    private String failureReason;

    /** Optimistic lock: concurrent webhook + reconciliation updates cannot silently overwrite each other. */
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    public static Payment create(String orderId, String idempotencyKey, String requestHash,
                                 long amount, String customerName, String customerEmail) {
        Payment p = new Payment();
        p.id = UUID.randomUUID();
        p.orderId = orderId;
        p.idempotencyKey = idempotencyKey;
        p.requestHash = requestHash;
        p.amount = amount;
        p.customerName = customerName;
        p.customerEmail = customerEmail;
        p.status = PaymentStatus.PENDING;
        p.createdAt = Instant.now();
        p.updatedAt = p.createdAt;
        return p;
    }

    /** @return true if the status actually changed */
    public boolean transitionTo(PaymentStatus next) {
        if (!status.canTransitionTo(next)) {
            return false;
        }
        status = next;
        updatedAt = Instant.now();
        return true;
    }

    public void markFailed(String reason) {
        if (transitionTo(PaymentStatus.FAILED)) {
            failureReason = reason;
        }
    }

    public void attachSnap(String token, String url) {
        this.snapToken = token;
        this.redirectUrl = url;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getOrderId() { return orderId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public long getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public String getCustomerName() { return customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public String getSnapToken() { return snapToken; }
    public String getRedirectUrl() { return redirectUrl; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

package dev.fahtur.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_events")
public class WebhookEvent {

    @Id
    private UUID id;

    @Column(name = "event_key", nullable = false, updatable = false)
    private String eventKey;

    @Column(name = "order_id", nullable = false, updatable = false)
    private String orderId;

    @Column(name = "transaction_status", nullable = false, updatable = false)
    private String transactionStatus;

    @Column(nullable = false, updatable = false)
    private String payload;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected WebhookEvent() {
    }

    public static WebhookEvent of(String eventKey, String orderId, String transactionStatus, String payload) {
        WebhookEvent e = new WebhookEvent();
        e.id = UUID.randomUUID();
        e.eventKey = eventKey;
        e.orderId = orderId;
        e.transactionStatus = transactionStatus;
        e.payload = payload;
        e.receivedAt = Instant.now();
        return e;
    }

    public UUID getId() { return id; }
    public String getEventKey() { return eventKey; }
    public String getOrderId() { return orderId; }
    public String getTransactionStatus() { return transactionStatus; }
}

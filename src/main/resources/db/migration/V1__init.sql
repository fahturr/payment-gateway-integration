CREATE TABLE payments (
    id              UUID         PRIMARY KEY,
    order_id        VARCHAR(64)  NOT NULL UNIQUE,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    request_hash    VARCHAR(64)  NOT NULL,
    amount          BIGINT       NOT NULL CHECK (amount > 0),
    currency        VARCHAR(3)   NOT NULL DEFAULT 'IDR',
    status          VARCHAR(20)  NOT NULL,
    customer_name   VARCHAR(255) NOT NULL,
    customer_email  VARCHAR(255) NOT NULL,
    snap_token      VARCHAR(255),
    redirect_url    VARCHAR(512),
    failure_reason  VARCHAR(255),
    version         BIGINT       NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_payments_status_created ON payments (status, created_at);

-- Audit log of every verified webhook; the unique key makes redelivery a no-op.
CREATE TABLE webhook_events (
    id                 UUID         PRIMARY KEY,
    event_key          VARCHAR(255) NOT NULL UNIQUE,
    order_id           VARCHAR(64)  NOT NULL,
    transaction_status VARCHAR(32)  NOT NULL,
    payload            TEXT         NOT NULL,
    received_at        TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_webhook_events_order ON webhook_events (order_id);

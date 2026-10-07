package dev.fahtur.payments.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "midtrans")
public record MidtransProperties(
        String serverKey,
        String snapBaseUrl,
        String apiBaseUrl,
        int maxAttempts,
        long initialBackoffMs,
        int connectTimeoutMs,
        int readTimeoutMs) {
}

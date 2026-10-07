package dev.fahtur.payments.midtrans;

import com.fasterxml.jackson.databind.JsonNode;
import dev.fahtur.payments.config.MidtransProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class MidtransClient {

    private static final Logger log = LoggerFactory.getLogger(MidtransClient.class);

    public record SnapTransaction(String token, String redirectUrl) {
    }

    private final MidtransProperties props;
    private final RestClient snap;
    private final RestClient api;

    public MidtransClient(MidtransProperties props, RestClient.Builder builder) {
        if (props.serverKey() == null || props.serverKey().isBlank()) {
            throw new IllegalStateException("midtrans.server-key (env MIDTRANS_SERVER_KEY) must be set");
        }
        this.props = props;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeoutMs());
        factory.setReadTimeout(props.readTimeoutMs());

        String auth = "Basic " + Base64.getEncoder()
                .encodeToString((props.serverKey() + ":").getBytes(StandardCharsets.UTF_8));

        this.snap = builder.clone()
                .baseUrl(props.snapBaseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, auth)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.api = builder.clone()
                .baseUrl(props.apiBaseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, auth)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public SnapTransaction createSnapTransaction(String orderId, long grossAmount,
                                                 String customerName, String customerEmail) {
        Map<String, Object> body = Map.of(
                "transaction_details", Map.of("order_id", orderId, "gross_amount", grossAmount),
                "customer_details", Map.of("first_name", customerName, "email", customerEmail));

        JsonNode res = withRetry("create snap transaction", () -> snap.post()
                .uri("/snap/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class));

        String token = res == null ? null : res.path("token").asText(null);
        String url = res == null ? null : res.path("redirect_url").asText(null);
        if (token == null) {
            throw new MidtransException("Midtrans response did not contain a Snap token");
        }
        return new SnapTransaction(token, url);
    }

    /**
     * Note: for unknown orders Midtrans answers HTTP 200 with "status_code": "404" in the body,
     * so callers must inspect status_code, not just the HTTP status.
     */
    public JsonNode getTransactionStatus(String orderId) {
        JsonNode res = withRetry("get transaction status", () -> api.get()
                .uri("/v2/{orderId}/status", orderId)
                .retrieve()
                .body(JsonNode.class));
        if (res == null) {
            throw new MidtransException("Empty status response for order " + orderId);
        }
        return res;
    }

    /** Exponential backoff with jitter; only network errors, 429 and 5xx are retried. */
    private <T> T withRetry(String operation, Supplier<T> call) {
        int attempt = 0;
        long backoff = props.initialBackoffMs();
        while (true) {
            attempt++;
            try {
                return call.get();
            } catch (RuntimeException e) {
                if (!isRetryable(e) || attempt >= props.maxAttempts()) {
                    throw new MidtransException(operation + " failed after " + attempt + " attempt(s)", e);
                }
                long sleep = backoff + ThreadLocalRandom.current().nextLong(backoff / 2 + 1);
                log.warn("{} failed (attempt {}/{}): {}. Retrying in {} ms",
                        operation, attempt, props.maxAttempts(), e.getMessage(), sleep);
                sleep(sleep);
                backoff *= 2;
            }
        }
    }

    static boolean isRetryable(RuntimeException e) {
        if (e instanceof ResourceAccessException) {
            return true;
        }
        if (e instanceof RestClientResponseException r) {
            int status = r.getStatusCode().value();
            return status >= 500 || status == 429;
        }
        return false;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new MidtransException("Interrupted while backing off", ie);
        }
    }
}

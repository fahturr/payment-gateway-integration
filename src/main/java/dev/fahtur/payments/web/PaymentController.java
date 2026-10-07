package dev.fahtur.payments.web;

import dev.fahtur.payments.error.BadRequestException;
import dev.fahtur.payments.service.PaymentService;
import dev.fahtur.payments.service.PaymentService.CreateResult;
import dev.fahtur.payments.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PaymentController {

    private final PaymentService payments;
    private final WebhookService webhooks;

    public PaymentController(PaymentService payments, WebhookService webhooks) {
        this.payments = payments;
        this.webhooks = webhooks;
    }

    @Operation(summary = "Create a payment (Midtrans Snap). Requires an Idempotency-Key header.")
    @PostMapping("/payments")
    public ResponseEntity<PaymentResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new BadRequestException("Idempotency-Key header is required (max 128 characters)");
        }
        CreateResult result = payments.create(idempotencyKey.trim(), request);
        PaymentResponse body = PaymentResponse.from(result.payment());
        if (result.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(body);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Get the current status of a payment")
    @GetMapping("/payments/{orderId}")
    public PaymentResponse get(@PathVariable String orderId) {
        return PaymentResponse.from(payments.get(orderId));
    }

    @Operation(summary = "Midtrans payment notification (HTTP notification URL). Signature-verified.")
    @PostMapping("/webhooks/midtrans")
    public Map<String, String> webhook(@RequestBody String rawBody) {
        return Map.of("outcome", webhooks.handle(rawBody).name());
    }
}

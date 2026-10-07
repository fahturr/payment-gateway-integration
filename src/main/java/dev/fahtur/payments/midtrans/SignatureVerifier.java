package dev.fahtur.payments.midtrans;

import dev.fahtur.payments.config.MidtransProperties;
import dev.fahtur.payments.util.Hashing;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

/**
 * Midtrans signs notifications as SHA512(order_id + status_code + gross_amount + ServerKey).
 * Comparison is constant-time to avoid leaking the signature through timing.
 */
@Component
public class SignatureVerifier {

    private final String serverKey;

    public SignatureVerifier(MidtransProperties props) {
        this.serverKey = props.serverKey();
    }

    public boolean isValid(String orderId, String statusCode, String grossAmount, String signature) {
        if (isBlank(orderId) || isBlank(statusCode) || isBlank(grossAmount) || isBlank(signature)) {
            return false;
        }
        String expected = Hashing.sha512Hex(orderId + statusCode + grossAmount + serverKey);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

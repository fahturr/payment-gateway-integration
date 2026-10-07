package dev.fahtur.payments.midtrans;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fahtur.payments.config.MidtransProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class SignatureVerifierTest {

    private static final String SERVER_KEY = "SB-Mid-server-test";

    private final SignatureVerifier verifier = new SignatureVerifier(
            new MidtransProperties(SERVER_KEY, "http://snap", "http://api", 3, 100, 1000, 1000));

    private static String sha512(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void acceptsCorrectSignature() throws Exception {
        String sig = sha512("INV-1" + "200" + "10000.00" + SERVER_KEY);
        assertTrue(verifier.isValid("INV-1", "200", "10000.00", sig));
    }

    @Test
    void acceptsUpperCaseHex() throws Exception {
        String sig = sha512("INV-1" + "200" + "10000.00" + SERVER_KEY).toUpperCase();
        assertTrue(verifier.isValid("INV-1", "200", "10000.00", sig));
    }

    @Test
    void rejectsTamperedAmount() throws Exception {
        String sig = sha512("INV-1" + "200" + "10000.00" + SERVER_KEY);
        assertFalse(verifier.isValid("INV-1", "200", "1.00", sig));
    }

    @Test
    void rejectsSignatureMadeWithAnotherKey() throws Exception {
        String sig = sha512("INV-1" + "200" + "10000.00" + "other-key");
        assertFalse(verifier.isValid("INV-1", "200", "10000.00", sig));
    }

    @Test
    void rejectsMissingFields() {
        assertFalse(verifier.isValid(null, "200", "10000.00", "abc"));
        assertFalse(verifier.isValid("INV-1", "", "10000.00", "abc"));
        assertFalse(verifier.isValid("INV-1", "200", "10000.00", null));
    }
}

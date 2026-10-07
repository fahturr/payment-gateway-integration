package dev.fahtur.payments.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Hashing {

    private Hashing() {
    }

    public static String sha256Hex(String input) {
        return digest("SHA-256", input);
    }

    public static String sha512Hex(String input) {
        return digest("SHA-512", input);
    }

    private static String digest(String algorithm, String input) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " not available", e);
        }
    }
}

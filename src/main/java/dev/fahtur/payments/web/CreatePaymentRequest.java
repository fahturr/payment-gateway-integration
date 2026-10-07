package dev.fahtur.payments.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreatePaymentRequest(
        @NotNull @Positive @Max(1_000_000_000L) Long amount,
        @NotBlank @Size(max = 100) String customerName,
        @NotBlank @Email @Size(max = 255) String customerEmail) {
}

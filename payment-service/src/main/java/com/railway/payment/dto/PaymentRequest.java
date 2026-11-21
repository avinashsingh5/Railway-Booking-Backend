package com.railway.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PaymentRequest(
        @NotNull Long reservationId,
        @NotBlank @Pattern(regexp = "(?i)UPI|CARD|NETBANKING", message = "must be UPI, CARD or NETBANKING") String paymentMethod) {
}

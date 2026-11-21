package com.railway.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** What the payment gateway sends back after the user pays (or fails to). */
public record CallbackRequest(
        @NotBlank String transactionId,
        @NotBlank @Pattern(regexp = "(?i)SUCCESS|FAILURE", message = "must be SUCCESS or FAILURE") String status) {
}

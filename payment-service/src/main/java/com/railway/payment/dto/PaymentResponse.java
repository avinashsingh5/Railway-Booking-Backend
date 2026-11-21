package com.railway.payment.dto;

import com.railway.payment.model.PaymentStatus;

import java.math.BigDecimal;

public record PaymentResponse(
        Long paymentId,
        Long reservationId,
        String transactionId,
        BigDecimal amount,
        PaymentStatus status,
        String paymentMethod,
        BigDecimal refundAmount,
        String message) {
}

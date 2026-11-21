package com.railway.reservation.dto;

/** payment-service -> reservation-service. status is SUCCESS or FAILED. */
public record PaymentResultRequest(Long paymentId, String transactionId, String status) {
}

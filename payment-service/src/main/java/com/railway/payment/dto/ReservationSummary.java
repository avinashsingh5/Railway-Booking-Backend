package com.railway.payment.dto;

import java.math.BigDecimal;

/** What payment-service needs to know about a reservation. */
public record ReservationSummary(Long id, Long userId, String status, BigDecimal totalFare) {
}

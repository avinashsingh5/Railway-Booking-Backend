package com.railway.reservation.dto;

import java.math.BigDecimal;

/**
 * amount = TOTAL refunded so far for this reservation (not just this cancellation), so repeating the
 * call is harmless. fullCancellation = the whole ticket is now cancelled.
 */
public record RefundRequest(Long reservationId, BigDecimal amount, boolean fullCancellation) {
}

package com.railway.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CancellationResponse(
        Long cancelId,
        String pnr,
        @Schema(description = "REFUNDED or NO_REFUND") String status,
        @Schema(description = "Refund for THIS cancellation") BigDecimal refundAmount,
        LocalDateTime cancelTime,
        @Schema(description = "FULL = the whole ticket is cancelled, PARTIAL = some passengers remain", example = "PARTIAL")
        String type,
        List<CancelledPassenger> cancelledPassengers,
        @Schema(description = "Passengers still travelling on this ticket (0 after a full cancellation)")
        int remainingPassengers,
        @Schema(description = "Value of the ticket for the remaining passengers (0.00 after a full cancellation)")
        BigDecimal remainingFare) {

    public record CancelledPassenger(Long id, String name, int seatNumber) {
    }
}

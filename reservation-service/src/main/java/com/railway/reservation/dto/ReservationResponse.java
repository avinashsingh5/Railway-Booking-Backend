package com.railway.reservation.dto;

import com.railway.reservation.model.TravelClass;
import com.railway.reservation.model.PassengerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import com.railway.reservation.model.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record ReservationResponse(
        Long id,
        String pnr,
        @Schema(description = "CONFIRMED, PARTIALLY_CANCELLED (some passengers cancelled), CANCELLED, PENDING_PAYMENT or EXPIRED")
        ReservationStatus status,
        String trainNumber,
        String trainName,
        String boardingStation,
        String boardingStationName,
        String destinationStation,
        String destinationStationName,
        LocalDate boardingDate,
        LocalTime departureTime,
        LocalDate arrivalDate,
        LocalTime arrivalTime,
        TravelClass travelClass,
        int distanceKm,
        @Schema(description = "Amount paid for the whole booking (never changes)")
        BigDecimal totalFare,
        @Schema(description = "Value of the passengers still travelling = totalFare minus the cancelled passengers' share")
        BigDecimal remainingFare,
        List<PassengerInfo> passengers,
        LocalDateTime createdAt,
        LocalDateTime paymentDueBy) {

    public record PassengerInfo(Long id, String name, int age, String gender, int seatNumber, PassengerStatus status) {
    }
}

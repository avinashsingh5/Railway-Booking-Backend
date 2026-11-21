package com.railway.reservation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** train-service -> reservation-service: which seats were held and what the journey costs. */
public record AllocationResponse(
        Long trainId,
        String trainNumber,
        String trainName,
        String boardingStation,
        String boardingStationName,
        String destinationStation,
        String destinationStationName,
        LocalDate runDate,          // date the train left its ORIGIN station (inventory key)
        LocalDate boardingDate,
        LocalTime departureTime,
        LocalDate arrivalDate,
        LocalTime arrivalTime,
        int distanceKm,
        List<Integer> seatNumbers,
        BigDecimal farePerPassenger,
        BigDecimal totalFare) {
}

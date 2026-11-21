package com.railway.reservation.dto;

import com.railway.reservation.model.TravelClass;

import java.time.LocalDate;

/** reservation-service -> train-service: "hold N seats for this journey segment". */
public record AllocationRequest(
        String holdRef,
        Long trainId,
        LocalDate journeyDate,      // date the passenger boards (at the boarding station)
        TravelClass travelClass,
        String boardingStation,     // station code, can be ANY stop on the route
        String destinationStation,  // station code, must come after boarding station
        int passengerCount) {
}

package com.railway.train.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** One train as seen for a specific boarding -> destination pair. */
public record TrainSearchResult(
        Long trainId,
        String trainNumber,
        String trainName,
        String boardingStation,
        String boardingStationName,
        String destinationStation,
        String destinationStationName,
        boolean intermediateBooking,   // true when the passenger does not ride the full route
        LocalDate boardingDate,
        LocalTime departureTime,
        LocalDate arrivalDate,
        LocalTime arrivalTime,
        int distanceKm,
        List<ClassAvailability> classes) {
}

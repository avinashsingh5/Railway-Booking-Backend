package com.railway.train.dto;

import com.railway.train.model.TravelClass;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record TrainDetailsResponse(
        Long id,
        String trainNumber,
        String name,
        String source,
        String destination,
        List<StopInfo> stops,
        List<ClassInfo> classes,
        @Schema(description = "Days of the week the train leaves its origin station (sorted Monday..Sunday)",
                example = "[\"MONDAY\", \"TUESDAY\", \"WEDNESDAY\", \"THURSDAY\", \"FRIDAY\"]")
        List<DayOfWeek> runningDays) {

    public record StopInfo(int sequence, String stationCode, String stationName,
                           LocalTime arrivalTime, LocalTime departureTime, int dayOffset, int distanceKm) {
    }

    public record ClassInfo(TravelClass travelClass, int totalSeats, BigDecimal farePerKm) {
    }
}

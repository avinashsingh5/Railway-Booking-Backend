package com.railway.train.dto;

import com.railway.train.model.TravelClass;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/** Admin request to create a train. The ORDER of the stops list is the route order. */
public record CreateTrainRequest(
        @Schema(example = "12951")
        @NotBlank String trainNumber,

        @Schema(example = "Mumbai - Delhi Express")
        @NotBlank String name,

        @NotNull @Size(min = 2, message = "must contain at least 2 stops") @Valid List<StopRequest> stops,

        @NotNull @Size(min = 1, message = "must contain at least 1 class") @Valid List<ClassRequest> classes,

        @Schema(description = "Days of the week the train LEAVES ITS ORIGIN station. "
                + "Optional: if omitted the train runs every day. If given it must not be empty.",
                example = "[\"MONDAY\", \"TUESDAY\", \"WEDNESDAY\", \"THURSDAY\", \"FRIDAY\"]")
        @Size(min = 1, message = "must not be empty (omit it to run every day)") List<DayOfWeek> runningDays) {

    public record StopRequest(
            @Schema(example = "ST") @NotBlank String stationCode,
            @Schema(example = "Surat") @NotBlank String stationName,
            @Schema(description = "null for the first stop", example = "19:45") LocalTime arrivalTime,
            @Schema(description = "null for the last stop", example = "19:50") LocalTime departureTime,
            @Schema(description = "0 = same day the train left its origin, 1 = next day ...", example = "0") @Min(0) int dayOffset,
            @Schema(description = "km from the origin station (first stop = 0, must increase)", example = "263") @Min(0) int distanceKm) {
    }

    public record ClassRequest(
            @Schema(example = "SL") @NotNull TravelClass travelClass,
            @Schema(example = "72") @Min(1) int totalSeats,
            @Schema(example = "0.60") @NotNull @DecimalMin("0.01") BigDecimal farePerKm) {
    }
}
